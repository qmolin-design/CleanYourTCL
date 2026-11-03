import AppKit
import Darwin

let center = NSWorkspace.shared.notificationCenter
let sleepObserver = center.addObserver(forName: NSWorkspace.willSleepNotification, object: nil, queue: nil) { _ in
    print("sleep")
    fflush(stdout)
    var descriptor = pollfd(fd: STDIN_FILENO, events: Int16(POLLIN), revents: 0)
    let deadline = Date().addingTimeInterval(10)
    while deadline.timeIntervalSinceNow > 0 {
        let milliseconds = Int32(max(0, deadline.timeIntervalSinceNow * 1000))
        if poll(&descriptor, 1, milliseconds) <= 0 { break }
        var byte: UInt8 = 0
        if read(STDIN_FILENO, &byte, 1) != 1 || byte == 10 { break }
    }
}
let wakeObserver = center.addObserver(forName: NSWorkspace.didWakeNotification, object: nil, queue: nil) { _ in
    print("wake")
    fflush(stdout)
}
RunLoop.current.run()
