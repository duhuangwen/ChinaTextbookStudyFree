import { spawnSync } from "child_process";

type Interpreter = { command: string; prefixArgs: string[] };

const MIN_VERSION_CHECK = "import sys; sys.exit(0 if sys.version_info >= (3, 9) else 1)";

function candidates(): Interpreter[] {
  const fromEnv = process.env.PYTHON?.trim();
  const defaults: Interpreter[] =
    process.platform === "win32"
      ? [
          { command: "py", prefixArgs: ["-3"] },
          { command: "python", prefixArgs: [] },
          { command: "python3", prefixArgs: [] },
        ]
      : [
          { command: "python3", prefixArgs: [] },
          { command: "python", prefixArgs: [] },
        ];
  return fromEnv ? [{ command: fromEnv, prefixArgs: [] }, ...defaults] : defaults;
}

// The Windows Store "python.exe" alias exists on PATH but only prints an install hint, so probe by running it.
function isUsable({ command, prefixArgs }: Interpreter): boolean {
  const probe = spawnSync(command, [...prefixArgs, "-c", MIN_VERSION_CHECK], { stdio: "ignore" });
  return probe.status === 0;
}

const interpreter = candidates().find(isUsable);
if (!interpreter) {
  console.error("未找到 Python 3.9+：请安装 Python 并加入 PATH，或通过环境变量 PYTHON 指定解释器路径。");
  process.exit(1);
}

const result = spawnSync(
  interpreter.command,
  [...interpreter.prefixArgs, "-X", "utf8", ...process.argv.slice(2)],
  { stdio: "inherit" },
);
if (result.error) {
  console.error(result.error.message);
  process.exit(1);
}
process.exit(result.status ?? 1);
