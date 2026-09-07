import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg'],
      manifest: {
        name: 'مصروفي',
        short_name: 'مصروفي',
        description: 'تتبّع المصروف الشخصي',
        lang: 'ar',
        dir: 'rtl',
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#F7F8F9',
        theme_color: '#F7F8F9',
        start_url: '/',
        icons: [
          { src: 'icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
        /*
         * قارئ الـPDF مستثنى من التحميل المسبق عن قصد.
         *
         * حجمه ~١٫٧ م.ب (الحزمة + العامل)، وميزة الاستيراد دي بتتستخدم من
         * وقت للتاني — تحميلها على كل واحد بيثبّت التطبيق تكلفة على الأغلبية
         * عشان أقلية. بتتحمّل أول مرة يستورد PDF بس.
         *
         * ⚠️ **الأثر المعلن:** أول استيراد PDF محتاج نت. بعدها بيتخزن في
         * كاش المتصفح. الاستيراد من CSV وباقي التطبيق شغالين بلا نت زي ما هما.
         */
        globIgnores: ['**/pdf-*.js', '**/pdf.worker*'],
        // العامل ~١٫٤ م.ب فوق حد ورك‌بوكس الافتراضي (٢ م.ب) — الحد معلن هنا
        maximumFileSizeToCacheInBytes: 3 * 1024 * 1024,
      },
    }),
  ],
})
