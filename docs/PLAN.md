# Super DeepSeek — পরিকল্পনা (v2)

লক্ষ্য: মালিকের নিজস্ব DeepSeek 2.7.5 (versionCode 296) স্ট্যান্ডঅ্যালোন APK-কে Morphe প্যাচে **Super DeepSeek** বানানো। শুধু এই একটি ভার্সন সাপোর্টেড।

## নিয়ম
- প্যাকেজ `com.deepseek.chat` থাকবে; লগইন, সেশন, CAPTCHA, anti-fraud কোড ছোঁয়া যাবে না (`verify_login_untouched.py`)।
- স্যান্ডবক্স, PRoot, কোড রানার আপাতত নেই। Generative UI নেই, শুধু আর্টিফ্যাক্ট।
- UI ও আর্টিফ্যাক্ট ডিজাইন হুবহু `themuhammad-personal/Super-Deepseek-` থেকে। সেটিংস লজিক ও প্রম্পটের কাঠামো `EdgeTypE/better-deepseek` থেকে (তার UI/আর্টিফ্যাক্ট নয়)।

## অফিশিয়াল অ্যাপে বসার জায়গা (বিল্ডে বিল্ডে নাম বদলায়, তাই Compose trace string ও string-resource id দিয়ে fingerprint)
| জায়গা | ফাইল |
|---|---|
| সেটিংস পেজ | `SettingsPage.kt`, রো `SettingItem.kt` |
| "+" প্যানেল | `UploadPanel.kt`, `UploadPanelActionButton.kt` |
| মেসেজ রেন্ডার | `Markdown.kt`, `MarkdownCodeBlockHeader.kt`, `MarkdownCodeBlockContent.kt` |
| ইউজার বাবল | `UserChatMessageBubble.kt` |
| প্রম্পট সারিয়ালাইজার / স্ট্রিম SET·APPEND | `DeepSeekFingerprints296.kt` (আগের হুক পুনঃব্যবহার) |

## ধাপ
| ধাপ | কাজ |
|---|---|
| P0 | রিপো নতুন করে সাজানো, CI বেসলাইন সবুজ করা |
| P1 | নাম/আইকন/স্প্ল্যাশ + ডিভাইস স্পাইক (ইনস্টল, লগইন, হুক চলে কিনা) |
| P2 | Advanced settings রো + সব পেজ ও ফিচার (প্রম্পট/পারসোনা, মেমরি, ভাষা, Deep Research, MCP, slash commands, projects, chat, data) |
| P3 | "+" প্যানেলে পাঁচটি নতুন কার্ড: Deep Research, Upload folder, Attach project, GitHub repo, Fetch web page |
| P4 | আর্টিফ্যাক্ট ইঞ্জিন: ` ```sds-genui ` পার্সার, কার্ড, ভিউয়ার, ভার্সন, লাইব্রেরি, docx/xlsx/pptx মডিউল |
| P5 | প্রম্পট বিল্ডার (`<SuperDeepSeek>` র্‍্যাপার, মডিউল লোডার, ফ্রিকোয়েন্সি, তারিখ/মেমরি) + ইউজার বাবলে লুকানো |
| P6 | বাংলাসহ ভাষা, পালিশ, রিলিজ |

প্রতি ধাপের গেট: CI বিল্ড পাস, লগইন অপরিবর্তিত, হুক স্থান DEX-এ যাচাই, তারপর মালিকের ফোনে ছোট চেকলিস্ট।
