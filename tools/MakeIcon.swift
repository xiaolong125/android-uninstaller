import AppKit
import Foundation
let folder = URL(fileURLWithPath: CommandLine.arguments[1])
try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
for size in [16,32,128,256,512] {
 for scale in [1,2] {
  let pixels = size * scale
  let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: pixels, pixelsHigh: pixels, bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
  NSGraphicsContext.saveGraphicsState()
  let ctx = NSGraphicsContext(bitmapImageRep: rep)!
  NSGraphicsContext.current = ctx
  ctx.cgContext.scaleBy(x: CGFloat(pixels)/1024, y: CGFloat(pixels)/1024)
  let rect = NSRect(x: 60,y:60,width:904,height:904)
  let bg = NSBezierPath(roundedRect: rect, xRadius: 210, yRadius: 210)
  NSGradient(starting: NSColor(calibratedRed:0.10,green:0.26,blue:0.43,alpha:1), ending: NSColor(calibratedRed:0.06,green:0.13,blue:0.25,alpha:1))!.draw(in: bg, angle: 55)
  NSColor(calibratedRed:0.40,green:0.90,blue:0.63,alpha:1).setFill()
  let head = NSBezierPath()
  head.move(to: NSPoint(x:262,y:480));head.line(to:NSPoint(x:262,y:530))
  head.curve(to:NSPoint(x:762,y:530),controlPoint1:NSPoint(x:262,y:830),controlPoint2:NSPoint(x:762,y:830))
  head.line(to:NSPoint(x:762,y:480));head.close();head.fill()
  let antenna = NSBezierPath();antenna.lineWidth=25;antenna.lineCapStyle = .round
  antenna.move(to:NSPoint(x:355,y:699));antenna.line(to:NSPoint(x:310,y:777));antenna.move(to:NSPoint(x:669,y:699));antenna.line(to:NSPoint(x:714,y:777));NSColor(calibratedRed:0.40,green:0.90,blue:0.63,alpha:1).setStroke();antenna.stroke()
  NSColor(calibratedRed:0.07,green:0.17,blue:0.29,alpha:1).setFill()
  for x in [380,604] { NSBezierPath(ovalIn:NSRect(x:x,y:563,width:40,height:40)).fill() }
  NSColor.white.setStroke()
  let tray = NSBezierPath();tray.lineWidth=28;tray.lineCapStyle = .round;tray.lineJoinStyle = .round
  tray.move(to:NSPoint(x:387,y:394));tray.line(to:NSPoint(x:405,y:260));tray.curve(to:NSPoint(x:619,y:260),controlPoint1:NSPoint(x:425,y:232),controlPoint2:NSPoint(x:600,y:232));tray.line(to:NSPoint(x:637,y:394))
  tray.move(to:NSPoint(x:364,y:414));tray.line(to:NSPoint(x:660,y:414))
  tray.move(to:NSPoint(x:468,y:442));tray.line(to:NSPoint(x:556,y:442))
  tray.stroke()
  NSGraphicsContext.restoreGraphicsState()
  let suffix = scale == 2 ? "@2x" : ""
  try rep.representation(using: .png, properties: [:])!.write(to:folder.appendingPathComponent("icon_\(size)x\(size)\(suffix).png"))
 }
}
// Encode a standard ICNS container from the generated PNG representations.
let entries: [(String,String)] = [("icp4","icon_16x16.png"),("icp5","icon_32x32.png"),("icp6","icon_32x32@2x.png"),("ic07","icon_128x128.png"),("ic08","icon_256x256.png"),("ic09","icon_512x512.png"),("ic10","icon_512x512@2x.png")]
func integer(_ value: Int) -> Data { var number = UInt32(value).bigEndian; return withUnsafeBytes(of: &number) { Data($0) } }
var body = Data()
for (type,name) in entries {
    let png = try Data(contentsOf: folder.appendingPathComponent(name))
    body.append(Data(type.utf8)); body.append(integer(png.count + 8)); body.append(png)
}
var icns = Data("icns".utf8); icns.append(integer(body.count+8)); icns.append(body)
try icns.write(to: URL(fileURLWithPath: CommandLine.arguments[2]))
