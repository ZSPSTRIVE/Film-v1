/** @type {import('tailwindcss').Config} */
export default {
    content: [
        "./index.html",
        "./src/**/*.{vue,js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            colors: {
                apple: {
                    blue: '#0A84FF',
                    dark: '#000000',
                    panel: '#1C1C1E',
                    glass: 'rgba(255, 255, 255, 0.05)',
                }
            },
            fontFamily: {
                sans: ['-apple-system', 'BlinkMacSystemFont', '"SF Pro Display"', '"PingFang SC"', 'sans-serif'],
            },
            boxShadow: {
                'apple': '0 10px 40px rgba(0, 0, 0, 0.2)',
                'apple-hover': '0 20px 60px rgba(0, 0, 0, 0.4)',
            },
            borderRadius: {
                'apple': '24px',
                'apple-btn': '12px',
            }
        },
    },
    plugins: [],
}
