Add-Type -AssemblyName System.Windows.Forms
Write-Host "Initial desktop: $([System.Windows.Forms.Screen]::PrimaryScreen.Bounds)"
try {
  Add-Type @"
  using System;
  using System.Runtime.InteropServices;
  [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Ansi)]
  public struct DEVMODE {
      [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)]
      public string dmDeviceName;
      public short dmSpecVersion;
      public short dmDriverVersion;
      public short dmSize;
      public short dmDriverExtra;
      public int dmFields;
      public int dmPositionX;
      public int dmPositionY;
      public int dmDisplayOrientation;
      public int dmDisplayFixedOutput;
      public short dmColor;
      public short dmDuplex;
      public short dmYResolution;
      public short dmTTOption;
      public short dmCollate;
      [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)]
      public string dmFormName;
      public short dmLogPixels;
      public int dmBitsPerPel;
      public int dmPelsWidth;
      public int dmPelsHeight;
      public int dmDisplayFlags;
      public int dmDisplayFrequency;
      public int dmICMMethod;
      public int dmICMIntent;
      public int dmMediaType;
      public int dmDitherType;
      public int dmReserved1;
      public int dmReserved2;
      public int dmPanningWidth;
      public int dmPanningHeight;
  }
  public class DisplaySettingsHelper {
      [DllImport("user32.dll", CharSet = CharSet.Ansi)]
      public static extern int EnumDisplaySettings(string lpszDeviceName, int iModeNum, ref DEVMODE lpDevMode);
      [DllImport("user32.dll", CharSet = CharSet.Ansi)]
      public static extern int ChangeDisplaySettings(ref DEVMODE lpDevMode, int dwFlags);
      public const int ENUM_CURRENT_SETTINGS = -1;
      public const int DM_PELSWIDTH = 0x00080000;
      public const int DM_PELSHEIGHT = 0x00100000;
      public static int SetResolution(int width, int height) {
          DEVMODE dm = new DEVMODE();
          dm.dmSize = (short)Marshal.SizeOf(typeof(DEVMODE));
          if (EnumDisplaySettings(null, ENUM_CURRENT_SETTINGS, ref dm) != 0) {
              dm.dmPelsWidth = width;
              dm.dmPelsHeight = height;
              dm.dmFields = DM_PELSWIDTH | DM_PELSHEIGHT;
              int test = ChangeDisplaySettings(ref dm, 0x02);
              if (test == 0) {
                  return ChangeDisplaySettings(ref dm, 0x01);
              }
              return ChangeDisplaySettings(ref dm, 0);
          }
          return -1;
      }
  }
"@
  $res = [DisplaySettingsHelper]::SetResolution(1920, 1080)
  Write-Host "ChangeDisplaySettings 1920x1080 result: $res"
} catch {
  Write-Host "ChangeDisplaySettings notice: $_"
}
if (Get-Command Set-DisplayResolution -ErrorAction SilentlyContinue) {
  Set-DisplayResolution -Width 1920 -Height 1080 -Force
}
Write-Host "Available desktop: $([System.Windows.Forms.Screen]::PrimaryScreen.Bounds)"
