# Smart Auto Sorter Community (web)

Next.js + Firebase starter for the existing `Smart-Auto-Shoter` repository.

## Features scaffolded
- Email/password Firebase authentication.
- Public/private/unlisted layout records in Firestore.
- Title, description, category slot assignments and generated SVG inventory preview.
- Owner-only edit/delete in the UI and Firestore ownership checks.
- Layout data model designed for the mod share-code adapter.

## Important compatibility limitation
The current Java `ShareCodeCodec` writes **SAS1** and serializes the layout name, color, manual-placement policy and per-item rules only. It does **not** serialize `categorySlots`. A web-generated SAS1 code cannot faithfully export/import category assignments until the mod codec is upgraded with a versioned SAS2 format (while keeping SAS1 decode support). This starter therefore stores full structured layout data in Firestore; it must not claim category layouts are mod-import compatible yet. Implement and test SAS2 in the mod before enabling the final share-code import/export buttons.

## Firebase setup
1. Create a Firebase project.
2. In Authentication, enable Email/Password.
3. Create a Cloud Firestore database.
4. Register a Web app and copy its config into `.env.local` using `.env.example` as the template. Do not commit `.env.local`.
5. Publish `firestore.rules` to Firestore Rules. Review rules in the Firebase Rules Simulator before launch.
6. Set the same environment variables in Vercel Project Settings > Environment Variables.

## Local development
Requires Node.js 20.9+.
```bash
cd web
npm install
cp .env.example .env.local
# Fill in real Firebase Web app values
npm run dev
```
Then open http://localhost:3000.

## Deploy on Vercel
1. Import this repository into Vercel.
2. Set Root Directory to `web`.
3. Add the `NEXT_PUBLIC_FIREBASE_*` environment variables.
4. Deploy.
5. Deploy Firestore rules separately in Firebase Console or with Firebase CLI.

Firebase web configuration values identify the Firebase project; Firestore Security Rules are the actual access control. Never add Admin SDK service-account credentials to the browser app.

## Security model
- Public: readable by everyone, only owner can edit/delete.
- Private: owner only.
- Unlisted: link/code is a locator, not a substitute for private access control. Treat unlisted layouts as shareable with anyone who has the link.
- Firestore rules must be tested before production; UI checks are not security.
