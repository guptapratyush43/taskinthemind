# Task in the Mind

A calm little Android app for tasks that **ring like a real alarm**, not a quiet notification.

- Add a task with a heading, description, date and time
- When it's time, your chosen tone loops until you act, with a floating pop-up and a full-screen alarm on the lock screen
- **Got it** marks it done; **Reschedule** gives 5 / 10 / 15 minutes or a draggable clock and date
- Lists as tabs (All tasks, Work, Groceries…), a Completed section, long-press to mark done or delete
- Pick any ringtone from the phone or your own audio files
- Optional **Google Drive backup** to Drive's hidden app folder: tasks, lists, settings and your custom tone. Restore on a new phone in a tap

## Download

Grab the latest APK from **[Releases](../../releases/latest)**.

Android may show a Play Protect "unknown app" notice because the app isn't from the Play Store yet. Tap **More details → Install anyway**.

## Privacy

No accounts, ads or analytics. Everything stays on your phone unless you turn on Drive backup, which stores one file in *your own* Drive's hidden app folder. See the [privacy policy](https://guptapratyush43.github.io/task-in-the-mind/privacy.html).

## Build

Android Studio's JDK 17, then:

```bash
./gradlew assembleDebug
```

Release builds are signed with a key kept outside this repo (`signing/`, git-ignored).
