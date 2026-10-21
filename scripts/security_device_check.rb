#!/usr/bin/env ruby
# frozen_string_literal: true

# Build assembleDebugAndroidTest first. Re-sign only the separate test APK;
# never uninstall or clear the user's application. Requires the release signing key.
require "json"
require "open3"
require "fileutils"
require "digest"

root = File.expand_path("..", __dir__)
Dir.chdir(root)
File.umask(0o077)
output_dir = File.join(root, "build/security-device")
FileUtils.mkdir_p(output_dir)
adb = ENV.fetch("ADB", "adb")
sdk = ENV["ANDROID_HOME"] || File.read("local.properties")[/^sdk.dir=(.+)$/, 1]
tools = Dir.glob(File.join(sdk, "build-tools/*")).select { |p| File.file?(File.join(p, "apksigner")) }
    .max_by { |p| File.basename(p).split(".").map(&:to_i) }
raise "Android build tools missing" unless tools
gradle = File.read("app/build.gradle.kts")
version = gradle[/versionName = "([^"]+)"/, 1]
code = gradle[/versionCode = (\d+)/, 1]

def run!(*args, env: {})
  output, status = Open3.capture2e(env, *args)
  raise "#{File.basename(args.first)} failed: #{output}" unless status.success?
  output
end

devices = run!(adb, "devices").lines.grep(/\tdevice$/)
raise "Exactly one authorized device is required" unless devices.size == 1
installed = run!(adb, "shell", "dumpsys", "package", "com.willdeep.android")
raise "Install the matching Release APK first" unless installed.include?("versionName=#{version}") && installed.match?(/versionCode=#{code}\b/)
test_source = "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
test_apk = File.join(output_dir, "security-instrumentation.apk")
run!(File.join(tools, "zipalign"), "-f", "-P", "16", "4", test_source, test_apk)
run!(File.join(tools, "apksigner"), "sign", "--ks", ".signing/willdeep-release.jks",
     "--ks-key-alias", "willdeep", "--ks-pass", "env:WILLDEEP_SIGNING_PASSWORD",
     "--key-pass", "env:WILLDEEP_SIGNING_PASSWORD", test_apk,
     env: { "WILLDEEP_SIGNING_PASSWORD" => File.read(".signing/password").strip })
run!(File.join(tools, "apksigner"), "verify", test_apk)
begin
  run!(adb, "install", "-r", "-t", test_apk)
rescue RuntimeError => error
  raise unless error.message.include?("INSTALL_FAILED_UPDATE_INCOMPATIBLE")
  # Only replace a conflicting disposable instrumentation package, never the application.
  run!(adb, "uninstall", "com.willdeep.android.test")
  run!(adb, "install", "-r", "-t", test_apk)
end
result = run!(adb, "shell", "am", "instrument", "-w", "-r", "-e", "class",
              "com.willdeep.android.SecurityConfigurationInstrumentedTest",
              "com.willdeep.android.test/androidx.test.runner.AndroidJUnitRunner")
File.write(File.join(output_dir, "instrumentation.txt"), result)
raise "Security instrumentation did not pass" unless result.include?("OK (1 test)") && !result.include?("FAILURES!!!")
report = { version: version, version_code: code.to_i, package: "com.willdeep.android",
           security_instrumentation: "passed", tests_passed: 1,
           backup_disabled: true, network_cleartext_disabled: true,
           app_data_cleared: false, test_apk_sha256: Digest::SHA256.file(test_apk).hexdigest }
File.write(File.join(output_dir, "report.json"), JSON.pretty_generate(report) + "\n")
File.write(File.join(output_dir, "report.md"), "# 真机安全验证\n\n" + report.map { |k, v| "- #{k}: #{v}" }.join("\n") + "\n")
puts JSON.pretty_generate(report)
