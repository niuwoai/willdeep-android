#!/usr/bin/env ruby
# frozen_string_literal: true

require 'fileutils'
require 'json'
require 'open3'
require 'optparse'
require 'rexml/document'
require 'time'

root = File.expand_path('..', __dir__)
options = {
  rust: File.expand_path('../willdeep-rs', root),
  mac: File.expand_path('../Xedit', root),
  java: ENV.fetch('JAVA_HOME', '/Users/rocky/Library/Java/JavaVirtualMachines/ms-21.0.8/Contents/Home')
}
OptionParser.new do |parser|
  parser.on('--rust-source PATH') { |path| options[:rust] = File.expand_path(path) }
  parser.on('--mac-source PATH') { |path| options[:mac] = File.expand_path(path) }
  parser.on('--java-home PATH') { |path| options[:java] = File.expand_path(path) }
  parser.on('--gradle-proxy HOST:PORT') { |value| options[:proxy] = value.split(':', 2) }
end.parse!

output = File.join(root, 'build', 'mobile-run-context-integration', Time.now.utc.strftime('%Y%m%dT%H%M%SZ'))
FileUtils.mkdir_p(output)
fixture_env = {
  'WILLDEEP_MOBILE_RUST_FIXTURE' => File.join(output, 'rust-capabilities.json'),
  'WILLDEEP_MOBILE_MAC_FIXTURE' => File.join(output, 'mac-capabilities.json')
}
report = { generated_at: Time.now.utc.iso8601, steps: [], fixtures: {}, device_acceptance: 'not_run', ok: false }

save = lambda do
  File.write(File.join(output, 'report.json'), JSON.pretty_generate(report))
  rows = report[:steps].map { |step| "| #{step[:name]} | #{step[:exit_code].zero? ? '通过' : '失败'} | #{step[:seconds]}s |" }
  text = "# 三端手机运行配置联调\n\n生成时间：#{report[:generated_at]}\n\n| 验证 | 结果 | 耗时 |\n| --- | --- | --- |\n#{rows.join("\n")}\n\n"
  text += "实际宿主回包：#{report[:fixtures].keys.join('、')}。\n\n"
  text += "真机验收：未执行。本报告验证实际模型 HTTP 请求、本机 WebSocket 中继、Mac 原生会话状态及 Android 实际协议解析，不代表真机安装、扫码或界面验收通过。\n"
  text += "\n#{report[:error]}\n" if report[:error]
  File.write(File.join(output, 'report.md'), text)
end

run = lambda do |name, directory, environment, command|
  started = Process.clock_gettime(Process::CLOCK_MONOTONIC)
  log_path = File.join(output, "#{name}.log")
  status = nil
  File.open(log_path, 'w') do |log|
    Open3.popen2e(environment, *command, chdir: directory) do |input, stream, wait|
      input.close
      stream.each_line { |line| log.write(line) }
      status = wait.value
    end
  end
  report[:steps] << { name: name, exit_code: status.exitstatus || 1,
                     seconds: (Process.clock_gettime(Process::CLOCK_MONOTONIC) - started).round(2), log: log_path }
  save.call
  puts "#{name}: #{status.success? ? '通过' : '失败'}"
  $stdout.flush
  raise "#{name} 失败，查看 #{log_path}" unless status.success?
end

begin
  run.call('rust_mobile_runtime', options[:rust], fixture_env,
           ['cargo', 'test', '-p', 'willdeep', 'daemon::mobile_gateway', '--', '--nocapture'])
  mac_env = fixture_env.merge('TEST_RUNNER_WILLDEEP_MOBILE_MAC_FIXTURE' => fixture_env.fetch('WILLDEEP_MOBILE_MAC_FIXTURE'))
  run.call('mac_native_context', options[:mac], mac_env,
           ['ruby', 'scripts/run_tests_fast.rb', '--force', '-only-testing:XeditTests/AgentMobileGatewayBridgeTests',
            '-only-testing:XeditTests/MobileRunContextTests', '-only-testing:XeditTests/MobileGatewayCoordinatorTests'])
  fixture_env.each do |key, path|
    envelope = JSON.parse(File.read(path))
    raise "缺失能力回包 #{key}" unless envelope['type'] == 'capabilities.updated' && envelope['session_id']
    raise "宿主未启用配置 #{key}" unless envelope.dig('payload', 'supports_configuration') == true
    report[:fixtures][key] = { path: path, type: envelope['type'], providers: envelope.dig('payload', 'providers').size,
                             models: envelope.dig('payload', 'models').size, skills: envelope.dig('payload', 'skills').size }
  end
  gradle = ['./gradlew', ':app:testDebugUnitTest', ':app:assembleDebug', ':app:lintDebug', '-Pkotlin.incremental=false', '--rerun-tasks']
  if options[:proxy]
    host, port = options[:proxy]
    raise '代理必须是 HOST:PORT' unless host && port&.match?(/\A\d+\z/)
    gradle.concat(["-Dhttp.proxyHost=#{host}", "-Dhttp.proxyPort=#{port}", "-Dhttps.proxyHost=#{host}", "-Dhttps.proxyPort=#{port}"])
  end
  run.call('android_cross_host_contract', root, fixture_env.merge('JAVA_HOME' => options[:java]), gradle)
  junit = REXML::Document.new(File.read(File.join(root, 'app/build/test-results/testDebugUnitTest/TEST-com.willdeep.android.ui.MobileRunContextTest.xml')))
  %w[parsesActualRustCapabilityEnvelopeWhenIntegrationFixtureIsProvided parsesActualMacCapabilityEnvelopeWhenIntegrationFixtureIsProvided].each do |name|
    test = REXML::XPath.first(junit, "//testcase[@name='#{name}']")
    raise "跨端解析未真正执行 #{name}" unless test && !test.elements['skipped'] && !test.elements['failure'] && !test.elements['error']
  end
  run.call('mac_go_gateway', File.join(options[:mac], 'willdeep-agent'), {}, ['go', 'test', './internal/agent'])
  run.call('mac_version_consistency', options[:mac], {}, ['ruby', 'scripts/version_consistency.rb'])
  report[:ok] = true
rescue StandardError => error
  report[:error] = error.message
ensure
  save.call
  puts "报告：#{File.join(output, 'report.md')}"
end
exit(report[:ok] ? 0 : 1)
