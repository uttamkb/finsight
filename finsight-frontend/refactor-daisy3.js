const fs = require('fs');
const path = require('path');

const mappings = {
  // Indigos -> Primary / Secondary
  'text-indigo-400': 'text-primary',
  'text-indigo-500': 'text-primary',
  'bg-indigo-500': 'bg-primary',
  'bg-indigo-500/10': 'bg-primary/10',
  'bg-indigo-500/20': 'bg-primary/20',
  'border-indigo-500': 'border-primary',
  'border-indigo-500/20': 'border-primary/20',
  'hover:bg-indigo-500': 'hover:bg-primary',
  'hover:text-indigo-500': 'hover:text-primary',
  'group-hover:bg-indigo-500': 'group-hover:bg-primary',
  'group-hover:text-indigo-500': 'group-hover:text-primary',

  // Emeralds -> Success
  'text-emerald-400': 'text-success',
  'text-emerald-500': 'text-success',
  'bg-emerald-500': 'bg-success',
  'bg-emerald-500/10': 'bg-success/10',
  'bg-emerald-500/20': 'bg-success/20',
  'border-emerald-500': 'border-success',
  'border-emerald-500/20': 'border-success/20',
  'hover:bg-emerald-500': 'hover:bg-success',

  // Ambers -> Warning
  'text-amber-400': 'text-warning',
  'text-amber-500': 'text-warning',
  'bg-amber-500': 'bg-warning',
  'bg-amber-500/10': 'bg-warning/10',
  'border-amber-500/20': 'border-warning/20',

  // Blues -> Info or Primary
  'text-blue-500': 'text-info',
  'bg-blue-500': 'bg-info',
  'bg-blue-500/10': 'bg-info/10',
  
  // Custom hex colors in Auditor page
  "'#00f0ff'": "'var(--color-primary)'",
  "'#ff8c00'": "'var(--color-warning)'",
  "'#facc15'": "'var(--color-accent)'",
  "'#4ade80'": "'var(--color-success)'",
  "'#f472b6'": "'var(--color-secondary)'",
  "'#0ea5e9'": "'var(--color-primary)'",
  "'#3b82f6'": "'var(--color-info)'",
  "'#6366f1'": "'var(--color-secondary)'",
  "'#8b5cf6'": "'var(--color-accent)'",
  "'#a855f7'": "'var(--color-neutral)'",
};

function processDirectory(directory) {
  const files = fs.readdirSync(directory);
  for (const file of files) {
    const fullPath = path.join(directory, file);
    const stat = fs.statSync(fullPath);
    if (stat.isDirectory()) {
      processDirectory(fullPath);
    } else if (fullPath.endsWith('.tsx') || fullPath.endsWith('.ts')) {
      let content = fs.readFileSync(fullPath, 'utf8');
      let originalContent = content;
      
      for (const [key, value] of Object.entries(mappings)) {
        // Safe string replacement without using word boundaries for exact strings containing special chars like / and #
        content = content.split(key).join(value);
      }

      if (content !== originalContent) {
        fs.writeFileSync(fullPath, content, 'utf8');
        console.log(`Updated: ${fullPath}`);
      }
    }
  }
}

processDirectory('/Users/uttamkumar_barik/Documents/Antigravity/java/finsight-frontend/src');
