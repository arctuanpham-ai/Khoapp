# POS0210 Firebase setup

1. Create a Firebase project on the Spark (free) plan.
2. Add an Android app with package `vn.ecohome.pos0210`.
3. Enable Authentication > Email/Password and create the owner's account.
4. Create Firestore in the nearest available region.
5. Publish `firestore.rules` from this directory.
6. In POS0210, open **Quản lý > Cloud & Manager realtime** and enter Project ID, Android App ID and Web API Key.
7. Sign in with the same owner account on each authorized POS/Manager device.

The app does not store the Firebase password. Room remains the operational source of truth. Version 1 uploads business/configuration data and provides a read-only realtime dashboard; it does not restore cloud data into Room or sync media.
