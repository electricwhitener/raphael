# Privacy

Raphael runs entirely on your computer. Your code, your programs' output and everything Raphael records stay there.

**What Raphael stores, on your computer only:**
- Your settings (theme, text size, limits), in Java's own preferences store.
- A copy of unsaved code (`draft.java`), deleted when you quit normally.
- A log of what Raphael did and any problems it hit, kept to three files of 1 MB each.

| System | Where |
|---|---|
| Windows | `%LOCALAPPDATA%\Raphael` (the log is in `logs\`) |
| macOS | `~/Library/Application Support/Raphael`, and the log in `~/Library/Logs/Raphael` |
| Linux | `~/.local/share/raphael`, and the log in `~/.local/state/raphael/logs` |

**What Raphael sends:** only one thing, once a day at most. It asks GitHub whether a newer version has been released (a request to `api.github.com` for this project's latest release). The request contains no information about you or your code; like any web request, GitHub can see that it came from your internet address. You can turn it off in Settings, under Updates, and then Raphael never connects to the internet.

Raphael has no analytics, no tracking and no accounts.
