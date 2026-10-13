#!/usr/bin/env ruby
# frozen_string_literal: true
require 'base64'
require 'digest'
require 'fileutils'
require 'json'
require 'net/http'
require 'open3'
require 'securerandom'
require 'socket'
require 'time'
require 'tmpdir'
require 'uri'

ROOT = File.expand_path('..', __dir__)
MAC = ENV.fetch('WILLDEEP_MAC_SOURCE', File.expand_path('../Xedit', ROOT))
RUST = ENV.fetch('WILLDEEP_RUST_BINARY', File.expand_path('../willdeep-rs/target/debug/willdeep', ROOT))
OUT = File.join(ROOT, 'build', 'mobile-phone-context', Time.now.utc.strftime('%Y%m%dT%H%M%SZ'))
FileUtils.mkdir_p(OUT)
File.umask(0o077)
secrets = []
report = { started_at: Time.now.utc.iso8601, steps: [], models: [], ok: false }
model_lock = Mutex.new
rooms = {}
room_lock = Mutex.new

def exact(socket, size)
  result = ''.b
  while result.bytesize < size
    part = socket.read(size - result.bytesize)
    return nil unless part
    result << part
  end
  result
end

def frame(text, opcode = 1)
  text = text.b
  header = if text.bytesize < 126
             [0x80 | opcode, text.bytesize].pack('CC')
           elsif text.bytesize < 65_536
             [0x80 | opcode, 126, text.bytesize].pack('CCn')
           else
             [0x80 | opcode, 127, text.bytesize].pack('CCQ>')
           end
  header + text
end

relay = TCPServer.new('127.0.0.1', 0)
relay_port = relay.addr[1]
relay_thread = Thread.new do
  loop do
    client = relay.accept
    Thread.new(client) do |socket|
      peer = nil
      room = nil
      begin
        request = socket.gets
        headers = {}
        while (line = socket.gets) && line != "\r\n"
          key, value = line.split(':', 2)
          headers[key.downcase] = value.to_s.strip
        end
        room = URI(request.split[1]).path.split('/').last
        expected = room_lock.synchronize { rooms.dig(room, :token) }
        raise 'unauthorized fixture relay' unless headers['authorization'] == "Bearer #{expected}"
        accept = Base64.strict_encode64(Digest::SHA1.digest(headers.fetch('sec-websocket-key') + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11'))
        socket.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: #{accept}\r\n\r\n")
        peer = { socket: socket, lock: Mutex.new }
        room_lock.synchronize { rooms.fetch(room)[:peers] << peer }
        loop do
          header = exact(socket, 2)
          break unless header
          first, second = header.unpack('CC')
          opcode = first & 15
          masked = (second & 128) != 0
          size = second & 127
          size = exact(socket, 2).unpack1('n') if size == 126
          size = exact(socket, 8).unpack1('Q>') if size == 127
          raise 'oversized fixture frame' if size > 4 * 1024 * 1024
          mask = masked ? exact(socket, 4).bytes : nil
          payload = exact(socket, size)
          break unless payload
          payload = payload.bytes.each_with_index.map { |byte, index| byte ^ mask[index % 4] }.pack('C*') if mask
          break if opcode == 8
          if opcode == 9
            peer[:lock].synchronize { socket.write(frame(payload, 10)) }
          elsif opcode == 1
            others = room_lock.synchronize { rooms.fetch(room)[:peers].reject { |p| p.equal?(peer) }.dup }
            others.each do |other|
              begin
                other[:lock].synchronize { other[:socket].write(frame(payload)) }
              rescue IOError, SystemCallError
                nil
              end
            end
          end
        end
      rescue StandardError
        nil
      ensure
        room_lock.synchronize { rooms[room][:peers].delete(peer) if room && rooms[room] && peer }
        socket.close rescue nil
      end
    end
  end
end

model = TCPServer.new('127.0.0.1', 0)
model_port = model.addr[1]
model_thread = Thread.new do
  loop do
    client = model.accept
    Thread.new(client) do |socket|
      begin
        method, path = socket.gets.split
        headers = {}
        while (line = socket.gets) && line != "\r\n"
          key, value = line.split(':', 2)
          headers[key.downcase] = value.to_s.strip
        end
        body = exact(socket, headers.fetch('content-length', '0').to_i)
        if method == 'GET' && path.end_with?('/models')
          response = JSON.generate(data: [{ id: 'remote-model' }, { id: 'other-model' }])
          kind = 'application/json'
        else
          request = JSON.parse(body)
          model_lock.synchronize { report[:models] << { model: request['model'], has_phone_prompt: request['messages'].to_s.include?('phone runtime proof'), messages: request['messages']&.size } }
          if request['stream']
            event = { id: 'fixture-response', model: request['model'], choices: [{ index: 0, delta: { role: 'assistant', content: 'fixture complete' }, finish_reason: 'stop' }] }
            response = "data: #{JSON.generate(event)}\n\ndata: [DONE]\n\n"
            kind = 'text/event-stream'
          else
            response = JSON.generate(id: 'fixture-response', choices: [{ index: 0, message: { role: 'assistant', content: 'fixture complete' }, finish_reason: 'stop' }])
            kind = 'application/json'
          end
        end
        socket.write("HTTP/1.1 200 OK\r\nContent-Type: #{kind}\r\nContent-Length: #{response.bytesize}\r\nConnection: close\r\n\r\n#{response}")
      rescue StandardError
        nil
      ensure
        socket.close rescue nil
      end
    end
  end
end

run = lambda do |name, command, environment = {}|
  text, status = Open3.capture2e(environment, *command, chdir: ROOT)
  secrets.each { |secret| text = text.gsub(secret, '[REDACTED]') }
  File.write(File.join(OUT, "#{name}.log"), text)
  report[:steps] << { name: name, exit_code: status.exitstatus || 1 }
  raise "#{name} failed; see isolated log" unless status.success?
  text
end
api = lambda do |state, operation, params|
  address = URI("http://#{state.fetch('address')}/v1/api")
  http = Net::HTTP.new(address.host, address.port, nil)
  request = Net::HTTP::Post.new(address)
  request['x-willdeep-token'] = state.fetch('token')
  request['content-type'] = 'application/json'
  request.body = JSON.generate(protocol_version: '1.0', request_id: SecureRandom.uuid, operation: operation, params: params)
  result = JSON.parse(http.request(request).body)
  raise "Runtime #{operation} failed" unless result['status'] == 'ok'
  result.fetch('data')
end
new_room = lambda do
  room = "wd-#{SecureRandom.hex(16)}"
  token = SecureRandom.hex(16)
  secrets.concat([room, token])
  room_lock.synchronize { rooms[room] = { token: token, peers: [] } }
  [room, token]
end
phone = lambda do |backend, room, token, skill|
  payload = JSON.generate(relay_base_url: "http://127.0.0.1:#{relay_port}", relay_room: room, relay_token: token,
                          desktop_name: "Live #{backend} fixture", protocol_version: 'mobile-gateway.v1')
  encoded = Base64.strict_encode64(payload)
  secrets.concat([payload, encoded])
  result = run.call("phone_#{backend}", ['adb', 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
      'com.willdeep.android.MobileRunContextInstrumentedTest', '-e', 'backend', backend,
      '-e', 'preferred_skill', skill, '-e', 'pairing_base64', encoded,
      'com.willdeep.android.contexttest.test/androidx.test.runner.AndroidJUnitRunner'])
  raise "phone #{backend} assertion failed" unless result.match?(/OK \(1 test\)/) && !result.include?('FAILURES!!!')
  %w[approval providers models skills reply].each do |screen|
    run.call("pull_#{backend}_#{screen}", ['adb', 'pull', "/sdcard/Android/data/com.willdeep.android.contexttest/files/#{backend}-#{screen}.png", File.join(OUT, "#{backend}-#{screen}.png")])
  end
  puts "phone #{backend}: passed"
  $stdout.flush
end

home = Dir.mktmpdir('wd-phone-', '/private/tmp')
FileUtils.mkdir_p(File.join(home, 'project', '.willdeep', 'skills', 'mobile-fixture'))
File.write(File.join(home, 'project', '.willdeep', 'skills', 'mobile-fixture', 'SKILL.md'), "---\nname: Mobile fixture\ndescription: isolated live fixture\n---\nCheck the fixture before changing code.\n")
File.write(File.join(home, 'config.toml'), "default_provider = 'alpha'\n[telemetry]\nenabled = false\n[providers.alpha]\nprovider = 'openai-compatible'\napi_base = 'http://127.0.0.1:#{model_port}/v1'\napi_key = 'test-only'\nmodel = 'configured-model'\n[providers.beta]\nprovider = 'openai-compatible'\napi_base = 'http://127.0.0.1:#{model_port}/v1'\napi_key = 'test-only'\nmodel = 'beta-model'\n")
rust_env = { 'WILLDEEP_HOME' => home, 'WILLDEEP_TELEMETRY_DISABLED' => '1' }
mac_wait = nil
mac_done = File.join(OUT, 'mac-done')
reverse_ready = false
begin
  run.call('device_ready', ['adb', 'wait-for-device'])
  run.call('install_test_app', ['adb', 'install', '-r', '-g', File.join(ROOT, 'app/build/outputs/apk/debug/app-debug.apk')])
  run.call('install_test_runner', ['adb', 'install', '-r', '-g', File.join(ROOT, 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')])
  run.call('reverse_relay', ['adb', 'reverse', "tcp:#{relay_port}", "tcp:#{relay_port}"])
  reverse_ready = true
  room, token = new_room.call
  File.write(File.join(home, 'mobile-relay.toml'), "relay_base_url = 'http://127.0.0.1:#{relay_port}'\nroom = '#{room}'\ntoken = '#{token}'\nenabled = true\n")
  run.call('start_isolated_runtime', [RUST, 'daemon', 'start'], rust_env)
  state = JSON.parse(File.read(File.join(home, 'runtime', 'daemon.json')))
  secrets << state.fetch('token')
  api.call(state, 'workspace.register', root: File.join(home, 'project'), access: 'smart', skills: ['mobile-fixture'])
  session = api.call(state, 'session.create', workspace: File.join(home, 'project'), title: 'phone configuration fixture')
  phone.call('rust', room, token, 'mobile-fixture')
  actual = api.call(state, 'session.get', id: session.fetch('id'))
  raise 'Rust phone configuration was not persisted' unless actual['profile'] == 'beta' && actual['model'] == 'remote-model' && actual['approval_mode'] == 'full_access'
  room, token = new_room.call
  ready = File.join(OUT, 'mac-ready.json')
  config_file = File.join(OUT, 'mac-live.json')
  File.write(config_file, JSON.generate(relay: "http://127.0.0.1:#{relay_port}", room: room, token: token, ready: ready,
                                       done: mac_done, model_base: "http://127.0.0.1:#{model_port}/v1"))
  mac_log = File.open(File.join(OUT, 'mac-live.log'), 'w')
  mac_pid = Process.spawn({ 'TEST_RUNNER_WILLDEEP_MOBILE_LIVE_CONFIG' => config_file, 'WILLDEEP_MOBILE_LIVE_CONFIG' => config_file },
                          'ruby', 'scripts/run_tests_fast.rb', '--force', '-only-testing:XeditTests/MobileRunContextLiveTests',
                          chdir: MAC, out: mac_log, err: mac_log)
  mac_wait = Thread.new { Process.wait2(mac_pid).last }
  deadline = Time.now + 600
  sleep 0.2 until File.file?(ready) || !mac_wait.alive? || Time.now > deadline
  raise 'Native Mac phone host did not become ready' unless File.file?(ready)
  native_skill = JSON.parse(File.read(ready)).fetch('skill_id')
  phone.call('mac', room, token, native_skill)
  File.write(mac_done, 'done')
  status = mac_wait.value
  report[:steps] << { name: 'mac_native_phone_host', exit_code: status.exitstatus || 1 }
  raise 'Native Mac phone assertions failed' unless status.success?
  raise 'Actual model requests were not observed' unless report[:models].count { |request| request[:model] == 'remote-model' && request[:has_phone_prompt] } >= 2
  report[:ok] = true
rescue StandardError => error
  report[:error] = error.message
ensure
  File.write(mac_done, 'done')
  if mac_wait&.alive? && !mac_wait.join(60)
    Process.kill('TERM', mac_pid)
    mac_wait.join(10)
  end
  begin
    run.call('stop_isolated_runtime', [RUST, 'daemon', 'stop'], rust_env) if File.file?(File.join(home, 'runtime', 'daemon.json'))
    run.call('remove_reverse', ['adb', 'reverse', '--remove', "tcp:#{relay_port}"]) if reverse_ready
  rescue StandardError => cleanup_error
    report[:cleanup_error] = cleanup_error.message
  end
  relay_thread.kill
  model_thread.kill
  relay.close
  model.close
  FileUtils.remove_entry(home)
  File.write(File.join(OUT, 'report.json'), JSON.pretty_generate(report))
  File.write(File.join(OUT, 'report.md'), "# 真机三端运行配置联调\n\n结果：#{report[:ok] ? '通过' : '失败'}\n\n" + report[:steps].map { |step| "- #{step[:name]}：#{step[:exit_code].zero? ? '通过' : '失败'}" }.join("\n") + "\n\n模型请求（仅记录模型和输入标记，不保存提示正文）：#{report[:models].map { |request| request[:model] }.join('、')}\n\n#{report[:error]}\n")
  puts "Report: #{File.join(OUT, 'report.md')}"
end
exit(report[:ok] ? 0 : 1)
