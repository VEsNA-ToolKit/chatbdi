const themeToggleButton = document.getElementById('theme-toggle-button');
const themeIcon = document.getElementById('theme-icon');
const rootElement = document.documentElement;

const currentTheme = localStorage.getItem('theme') || 'dark-theme';
rootElement.classList.add(currentTheme);

if (currentTheme === 'light-theme') {
    themeIcon.classList.remove('fa-moon');
    themeIcon.classList.add('fa-sun');
}

themeToggleButton.addEventListener('click', () => {
    if (rootElement.classList.contains('dark-theme')) {
        rootElement.classList.remove('dark-theme');
        rootElement.classList.add('light-theme');
        localStorage.setItem('theme', 'light-theme');

        themeIcon.classList.remove('fa-moon');
        themeIcon.classList.add('fa-sun');
    } else {
        rootElement.classList.remove('light-theme');
        rootElement.classList.add('dark-theme');
        localStorage.setItem('theme', 'dark-theme');

        themeIcon.classList.remove('fa-sun');
        themeIcon.classList.add('fa-moon');
    }
});
