#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "open3"
require "fileutils"
require "optparse"
require "time"
require "securerandom"

options = { workspace: "/private/tmp/willdeep-mobile-https-acceptance-20261021" }
OptionParser.new do |parser|
  parser.on("--backend NAME") { |v| options[:backend] = v }
  parser.on("--credential ID") { |v| options[:credential] = v }
  parser.on("--workspace PATH") { |v| options[:workspace] = v }
  parser.on("--connection-only") { options[:connection_only] = true }
end.parse!
raise "backend must be mac or rust" unless %w[mac rust].include?(options[:backend])
raise "credential must be a hashed device id" unless options[:credential].to_s.match?(/\A[0-9a-f]{24}\z/)
root = File.expand_path("..", __dir__)
File.umask(0o077)
directory = File.join(root, "build/mobile-live", options[:backend])
FileUtils.mkdir_p(directory)
marker = (100_000 + SecureRandom.random_number(900_000)).to_s
command = [ENV.fetch("ADB", "adb"), "shell", "am", "instrument", "-w", "-r", "-e", "class",
           "com.willdeep.android.LiveHostRelayInstrumentedTest#exerciseHost",
           "-e", "credential_id", options[:credential], "-e", "workspace", options[:workspace],
           "-e", "marker", marker]
command += ["-e", "connection_only", "true"] if options[:connection_only]
command << "com.willdeep.android.test/androidx.test.runner.AndroidJUnitRunner"
output = +""
status = nil
File.open(File.join(directory, "instrumentation.txt"), "w", 0o600) do |log|
  Open3.popen2e(*command) do |input, stream, wait|
    input.close
    stream.each_line do |line|
      output << line
      log.write(line)
      log.flush
      puts line if line.include?("live_") || line.include?("FAIL") || line.include?("OK (")
      STDOUT.flush
    end
    status = wait.value
  end
end
required = %w[session_list]
required += %w[isolated_session message_ack approval_received approval_applied assistant_reply] unless options[:connection_only]
stages = required.to_h { |stage| [stage, output.match?(/live_#{stage}=passed/)] }
passed = status.success? && output.include?("OK (1 test)") && stages.values.all?
report = { backend: options[:backend], tested_at: Time.now.iso8601, passed: passed,
           transport: "HTTPS/WSS public relay", stages: stages, marker: marker,
           approval_receipt: output[/live_approval_receipt=(\w+)/, 1],
           existing_app_data_cleared: false, approval_scope: "new session; exact ruby -e constant output only" }
File.write(File.join(directory, "report.json"), JSON.pretty_generate(report) + "\n")
File.write(File.join(directory, "report.md"), "# #{options[:backend]} 手机实测\n\n" + report.map { |k, v| "- #{k}: #{v}" }.join("\n") + "\n")
puts JSON.pretty_generate(report)
exit(passed ? 0 : 1)
