# الخطوات اليدوية المتبقية (تُنفَّذ لاحقًا مع لقطات الشاشة)

## Firebase (مشروع StockCaisse)
1. **بصمة مفتاح التجربة**: Paramètres du projet ← التطبيق com.elyedali.makhzan ← Ajouter une empreinte:
   `BF:56:4D:42:E2:E4:13:50:7B:64:E2:96:ED:8E:45:07:66:F2:94:69`
2. **الدخول بحساب Google**: Authentication ← Méthode de connexion ← Google ← Activer.
3. **قاعدة البيانات**: Firestore Database ← Créer (وضع Production) ← تبويب Règles ← لصق محتوى ملف `firestore.rules` ← Publier.

## Google Cloud (من Firebase: Afficher dans Google Cloud)
4. **تفعيل Google Drive API**.
5. **شاشة الموافقة OAuth**: إكمال الاسم وبريد الدعم ثم **Publish app** (وليس Testing).

## GitHub
6. سرّا التوقيع: `KEYSTORE_BASE64` و `KEYSTORE_PASSWORD`.
7. بصمة مفتاح النشر في Firebase:
   `E8:10:DF:49:02:BC:CD:62:F6:3F:1D:F9:A8:67:86:FF:C1:D4:34:C4`

## هاتف المسؤول (elyedalimoctar@gmail.com)
8. إعدادات الهاتف ← التطبيقات ← تسيير المخزون ← الفتح افتراضيًا ← إضافة الرابط `stockcaisse.web.app`.

## Google Play Console (مع المرافقة خطوة بخطوة)
9. إنشاء التطبيق ورفع ملف `makhzan-aab`.
10. المنتجات داخل التطبيق: `shop_tier_1` و `shop_tier_2` و `shop_tier_3` (400 / 600 / 900 أوقية، 4 أشهر).
11. بصمة SHA-1 لتوقيع Google Play (Intégrité de l'application) ← إضافتها في Firebase.

## AdMob
12. إنشاء حساب وتطبيق ووحدة Bannière، ثم إرسال **معرّف التطبيق** و**معرّف الوحدة** لاستبدال المعرّفات التجريبية.
13. في Play Console: التصريح بأن التطبيق يحتوي إعلانات + قسم Sécurité des données.
