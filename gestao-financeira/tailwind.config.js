/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: 'class',
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#effcf8',
          100: '#d7f8ef',
          200: '#b0efd9',
          300: '#7ddfc0',
          400: '#44caa2',
          500: '#24ad88',
          600: '#168b6e',
          700: '#146f5a',
          800: '#14584a',
          900: '#12493d'
        }
      },
      fontFamily: {
        sans: ['Manrope', 'system-ui', 'sans-serif']
      },
      boxShadow: {
        card: '0 14px 36px rgba(10, 24, 20, 0.08)'
      }
    }
  },
  plugins: []
};
