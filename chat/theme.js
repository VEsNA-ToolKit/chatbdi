const themeToggleButton = document.getElementById('theme-toggle-button');
const themeIcon = document.getElementById('theme-icon');
const rootElement = document.documentElement;

// Imposta il tema iniziale in base alla preferenza utente o tema scuro predefinito
const currentTheme = localStorage.getItem('theme') || 'dark-theme';
rootElement.classList.add(currentTheme);

// Imposta l'icona iniziale in base al tema
if (currentTheme === 'light-theme') {
    themeIcon.classList.remove('fa-moon');
    themeIcon.classList.add('fa-sun');
}

themeToggleButton.addEventListener('click', () => {
    if (rootElement.classList.contains('dark-theme')) {
        rootElement.classList.remove('dark-theme');
        rootElement.classList.add('light-theme');
        localStorage.setItem('theme', 'light-theme');

        // Cambia l'icona a sole
        themeIcon.classList.remove('fa-moon');
        themeIcon.classList.add('fa-sun');
    } else {
        rootElement.classList.remove('light-theme');
        rootElement.classList.add('dark-theme');
        localStorage.setItem('theme', 'dark-theme');

        // Cambia l'icona a luna
        themeIcon.classList.remove('fa-sun');
        themeIcon.classList.add('fa-moon');
    }
});
