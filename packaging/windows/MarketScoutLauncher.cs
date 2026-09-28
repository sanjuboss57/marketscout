using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Threading;
using System.Windows.Forms;

namespace MarketScout
{
    static class Program
    {
        [STAThread]
        static void Main()
        {
            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            Directory.SetCurrentDirectory(baseDir);

            string jarPath = Path.Combine(baseDir, @"target\marketscout-0.0.1-SNAPSHOT.jar");
            string appUrl = "http://localhost:18080";
            string logPath = Path.Combine(baseDir, "launcher.log");

            try
            {
                File.AppendAllText(logPath, DateTime.Now + ": Launcher started.\n");

                bool isRunning = IsServerReady(appUrl);
                if (!isRunning)
                {
                    if (!File.Exists(jarPath))
                    {
                        MessageBox.Show("MarketScout JAR not found at:\n" + jarPath + "\nPlease run build first.", "MarketScout", MessageBoxButtons.OK, MessageBoxIcon.Error);
                        return;
                    }

                    string javaBinary = FindJavaBinary();
                    File.AppendAllText(logPath, DateTime.Now + ": Using Java binary: " + javaBinary + "\n");

                    ProcessStartInfo psi = new ProcessStartInfo();
                    psi.FileName = javaBinary;
                    psi.Arguments = "-Xmx1024m -Xms256m -jar \"" + jarPath + "\"";
                    psi.WorkingDirectory = baseDir;
                    psi.UseShellExecute = true; // Run as independent process

                    Process p = Process.Start(psi);
                    File.AppendAllText(logPath, DateTime.Now + ": Started independent process PID: " + (p != null ? p.Id.ToString() : "null") + "\n");

                    // Poll until server is ready (up to 40 seconds)
                    int retries = 80;
                    while (retries > 0 && !IsServerReady(appUrl))
                    {
                        Thread.Sleep(500);
                        retries--;
                    }
                    File.AppendAllText(logPath, DateTime.Now + ": Server ready check: " + IsServerReady(appUrl) + " (remaining retries: " + retries + ")\n");
                }
                else
                {
                    // Server was already running, bring up the window
                    OpenAppWindow(appUrl);
                    File.AppendAllText(logPath, DateTime.Now + ": Server already active; brought window up.\n");
                }
            }
            catch (Exception ex)
            {
                File.AppendAllText(logPath, DateTime.Now + ": Error: " + ex + "\n");
                MessageBox.Show("MarketScout Launcher Error:\n" + ex.Message, "MarketScout", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        static string FindJavaBinary()
        {
            string[] envHomes = new string[]
            {
                Environment.GetEnvironmentVariable("JAVA_HOME", EnvironmentVariableTarget.Process),
                Environment.GetEnvironmentVariable("JAVA_HOME", EnvironmentVariableTarget.Machine),
                Environment.GetEnvironmentVariable("JAVA_HOME", EnvironmentVariableTarget.User)
            };

            foreach (string home in envHomes)
            {
                if (!string.IsNullOrEmpty(home))
                {
                    string p = Path.Combine(home.TrimEnd('\\', '/'), @"bin\javaw.exe");
                    if (File.Exists(p)) return p;
                    p = Path.Combine(home.TrimEnd('\\', '/'), @"bin\java.exe");
                    if (File.Exists(p)) return p;
                }
            }

            string[] commonPaths = new string[]
            {
                @"C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\bin\javaw.exe",
                @"C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\bin\java.exe",
                @"C:\Program Files\Eclipse Adoptium\jdk-21\bin\javaw.exe",
                @"C:\Program Files\Java\jdk-21\bin\javaw.exe"
            };

            foreach (string cp in commonPaths)
            {
                if (File.Exists(cp)) return cp;
            }

            return "javaw.exe";
        }

        static bool IsServerReady(string url)
        {
            try
            {
                HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url + "/api/app/info");
                req.Timeout = 1200;
                req.Method = "GET";
                using (HttpWebResponse resp = (HttpWebResponse)req.GetResponse())
                {
                    return resp.StatusCode == HttpStatusCode.OK;
                }
            }
            catch
            {
                return false;
            }
        }

        static void OpenAppWindow(string url)
        {
            string[] edgePaths = new string[]
            {
                @"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
                @"C:\Program Files\Microsoft\Edge\Application\msedge.exe"
            };

            foreach (string p in edgePaths)
            {
                if (File.Exists(p))
                {
                    try
                    {
                        ProcessStartInfo psi = new ProcessStartInfo(p, "--app=" + url);
                        psi.UseShellExecute = true;
                        Process.Start(psi);
                        return;
                    }
                    catch { }
                }
            }

            // Fallback: standard system browser
            try
            {
                Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
            }
            catch { }
        }
    }
}
