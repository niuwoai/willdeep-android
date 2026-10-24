#!/usr/bin/env ruby
# frozen_string_literal: true

require "fileutils"
require "json"
require "open3"
require "rexml/document"
require "time"

Encoding.default_external = Encoding::UTF_8
root = File.expand_path("..", __dir__)
rust = ENV.fetch("WILLDEEP_RUST_QUEUE_ROOT", File.expand_path("../willdeep-rs", root))
mac = ENV.fetch("WILLDEEP_MAC_QUEUE_ROOT", File.expand_path("../Xedit", root))
output = File.join(root, "build", "mobile-queue-verification")
FileUtils.mkdir_p(output)
if File.file?(File.join(output, "report.json"))
  archive = File.join(output, "history", Time.now.strftime("%Y%m%d-%H%M%S"))
  FileUtils.mkdir_p(archive)
  Dir[File.join(output, "*")].select { |path| File.file?(path) }.each { |path| FileUtils.cp(path, archive) }
end
fixtures = {
  "WILLDEEP_QUEUE_RUST_FIXTURE" => File.join(output, "rust-snapshot.json"),
  "WILLDEEP_QUEUE_MAC_FIXTURE" => File.join(output, "mac-snapshot.json")
}
fixtures.each_value { |path| File.delete(path) if File.file?(path) }

def run_stage(name, cwd, command, environment, output)
  log = File.join(output, "#{name}.log")
  puts "Running #{name}; log: #{log}"
  started = Process.clock_gettime(Process::CLOCK_MONOTONIC)
  status = nil
  Open3.popen2e(environment, *command, chdir: cwd) do |input, stream, wait|
    input.close
    File.open(log, "w") do |file|
      file.sync = true
      stream.each_line { |line| file.write(line) }
    end
    status = wait.value.exitstatus
  end
  result = { name: name, cwd: cwd, command: command, exit_code: status,
             seconds: (Process.clock_gettime(Process::CLOCK_MONOTONIC) - started).round(2), log: log }
  puts "#{name}: exit=#{status} (#{result[:seconds]}s)"
  result
end

stages = []
begin
  stages << run_stage("rust", rust, ["cargo", "test", "-p", "willdeep", "daemon::mobile_gateway::tests", "-j", "4", "--", "--test-threads=4"], fixtures, output)
  mac_environment = fixtures.merge("TEST_RUNNER_WILLDEEP_QUEUE_MAC_FIXTURE" => fixtures.fetch("WILLDEEP_QUEUE_MAC_FIXTURE"))
  # The fixture is a test side effect; a cached verdict cannot regenerate it.
  stages << run_stage("mac", mac, ["ruby", "scripts/run_tests_fast.rb", "--force", "-only-testing:XeditTests/AgentMobileGatewayBridgeTests"], mac_environment, output)
  # Never reuse a stale cross-platform fixture from an earlier run.
  valid_fixtures = stages.all? { |stage| stage[:exit_code].zero? } && fixtures.values.all? { |file| File.file?(file) }
  if valid_fixtures
    stages << run_stage("android", root, ["./gradlew", "testReleaseUnitTest", "assembleRelease", "assembleDebugAndroidTest", "lintRelease", "--rerun-tasks", "--console=plain"], fixtures, output)
  end
ensure
  tests = { tests: 0, failures: 0, errors: 0, skipped: 0 }
  if stages.any? { |stage| stage[:name] == "android" }
    Dir[File.join(root, "app/build/test-results/testReleaseUnitTest/TEST-*.xml")].each do |path|
      suite = REXML::Document.new(File.read(path)).root
      tests.each_key { |key| tests[key] += suite.attributes[key.to_s].to_i }
    end
  end
  passed = stages.length == 3 && stages.all? { |stage| stage[:exit_code].zero? }
  report = { status: passed ? "passed" : "failed", generated_at: Time.now.iso8601,
             stages: stages, android_tests: tests, fixtures: fixtures,
             device_acceptance: "not_run", scope: "Real Rust relay/control API, real Mac AppState/router, Android decoder/editor contract and Release checks" }
  File.write(File.join(output, "report.json"), JSON.pretty_generate(report) + "\n")
  rows = stages.map { |stage| "| #{stage[:name]} | #{stage[:exit_code]} | #{stage[:seconds]} | #{stage[:log]} |" }
  markdown = ["# 三端待发队列验证", "", "状态：#{report[:status]}", "", "| 阶段 | 退出码 | 秒 | 日志 |", "|---|---:|---:|---|", *rows,
              "", "Android：#{tests[:tests]} tests，#{tests[:failures]} failures，#{tests[:errors]} errors，#{tests[:skipped]} skipped。",
              "", "范围：真实 Rust WebSocket 中继与控制面、真实 Mac AppState/router，以及 Android 对两端快照的解码与编辑协议。设备交互验收未运行。", ""]
  File.write(File.join(output, "report.md"), markdown.join("\n"))
end
exit(report[:status] == "passed" ? 0 : 1)
