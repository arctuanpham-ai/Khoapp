# POS0210 Web Manager

Static, read-only Firebase Hosting dashboard. It reads the existing store path:

`/users/X9ln8CP7UtbOLOHfipYMCjfbb1p1/stores/0210`

Deploy after Firebase CLI login:

```bash
firebase deploy --only firestore:rules,hosting
```

For a future shareholder account, create its Firebase Auth user then create this document from the Firebase Console (admin access bypasses client rules):

`managerAccess/<shareholder Firebase UID>`

```json
{ "ownerUid": "X9ln8CP7UtbOLOHfipYMCjfbb1p1", "active": true }
```
