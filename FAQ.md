# Mjolnir FAQ (0.2.7)

## What is Mjolnir?
Mjolnir is a dual‑screen home layer for Android handhelds. It sits on top of your default home launcher and lets you map different apps to different screens, then route Home button presses to exactly what you want.

## What does it do in practice?
It gives you a “two‑screen launcher” experience:
- Top screen can be a game frontend, bottom can be your Android launcher.
- Or run two launchers at once.
- Or keep a helper app (notes, browser, tools) on the second screen.

You choose the apps and how Home behaves.

## What do I need installed to get the most out of Mjolnir?
Mjolnir works best if you already have **at least two other apps/frontends/launchers** you want to access from the Home button. If you install Mjolnir on a brand‑new device with no other frontends, it won’t have much to route to and the experience will feel incomplete. Install a couple launchers or frontends first, then set up Mjolnir.

## Does it replace my default home?
Not necessarily. Mjolnir can act as your default home, *or* you can keep another launcher as default and let Mjolnir manage what shows on each screen.

## What devices does it support?
Any dual‑screen Android device may work, but compatibility varies by OEM quirks. Current testing is centered on AYN Thor.

**Important**: As of 0.2.7, the RG‑DS is **not officially supported** and does **not** work in current testing. RG‑DS support is planned for 0.2.8.

## What’s “Basic” vs “Advanced” mode?
- **Basic**: Mjolnir just launches your selected top/bottom apps. No home button interception.
- **Advanced**: Mjolnir intercepts Home button gestures and applies your preset behaviors.

If you want gesture routing, use Advanced.

> Note: 0.2.8 will remove the Basic/Advanced split.

## What is the “SafetyNet” screen?
It’s a tiny fallback activity that lives at the bottom of each display’s stack. If a screen becomes “empty” due to a launcher crash or bad config, SafetyNet prevents a soft‑lock. You should never normally see it — unless something went wrong.

## Why can’t I pick Mjolnir itself as a top/bottom app?
Because it causes recursion and can soft‑lock the system. Mjolnir is permanently blacklisted from the app picker to prevent this.

## What’s the “Main Screen” setting?
Right now it’s mostly a future hook. The idea is to define which screen should get focus when launching both apps at once. It’s not fully enforced yet, but will matter more later.

## What are gesture presets?
Presets are saved Home‑button behaviors. You pick one and it controls what single/double/triple/long‑press do. You can edit or copy presets.

## Does Mjolnir support controller navigation?
Yes — most screens can be navigated with d‑pad/analog + A/B, and in onboarding L/R for Back/Next when available. There are still some edge cases I’m ironing out.

## Why is the notification so important?
It’s your quick access point. It shows status, lets you open settings, and provides control over core behaviors. More advanced profile switching will come in 0.2.8.

## How do I reset if I break my config?
All settings are stored in `/Android/data/xyz.blacksheep.mjolnir/`. You can delete or edit:
- `settings.json`
- `config.ini`
- `blacklist.json`
- `gestures/*.cfg`

Restart Mjolnir and it will rebuild defaults.

## Where do logs go?
Logs are stored in `/Android/data/xyz.blacksheep.mjolnir/logs/`. If something breaks, check there first.

## Where is the Discord server?
Join here: https://discord.gg/SByZdew8Kw

## How do I share presets or configs?
Gesture presets live in `/gestures/`. You can share the `.cfg` files directly.

## Is this stable?
It’s a public beta. It’s *usable* and I daily‑drive it, but it’s still evolving fast. Report issues and I’ll fix them.
