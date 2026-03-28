const fs = require('fs');
const path = require('path');

const mappings = {
  // Whites and light colors
  'bg-white': 'bg-base-100',
  'text-white': 'text-primary-content',
  'border-white/10': 'border-base-content/10',
  'border-white/20': 'border-base-content/20',

  // Grays / Neutrals
  'text-gray-300': 'text-base-content/80',
  'text-gray-400': 'text-base-content/60',
  'text-gray-500': 'text-base-content/60',
  'text-gray-600': 'text-base-content/70',
  'text-neutral-400': 'text-base-content/60',
  'text-neutral-500': 'text-base-content/60',
  'bg-gray-100': 'bg-base-200',
  'bg-gray-50': 'bg-base-200',
  'bg-gray-800': 'bg-base-300',
  'bg-gray-900': 'bg-base-300',
  'bg-neutral-800': 'bg-base-300',
  'bg-neutral-900': 'bg-base-300',
  'border-gray-200': 'border-base-content/20',
  'border-gray-700': 'border-base-content/20',
  'border-gray-800': 'border-base-content/20',
  'border-neutral-200': 'border-base-content/20',
  'border-neutral-800': 'border-base-content/20',

  // Reds / Destructives
  'bg-red-500': 'bg-error',
  'bg-red-500/10': 'bg-error/10',
  'bg-red-500/20': 'bg-error/20',
  'text-red-500': 'text-error',
  'text-red-400': 'text-error',
  'border-red-500/20': 'border-error/20',

  // Greens
  'bg-green-500': 'bg-success',
  'bg-green-500/10': 'bg-success/10',
  'text-green-500': 'text-success',
  'border-green-500/20': 'border-success/20',

  // Yellows
  'bg-yellow-500': 'bg-warning',
  'bg-yellow-500/10': 'bg-warning/10',
  'text-yellow-500': 'text-warning',
  'border-yellow-500/20': 'border-warning/20',
  // Print Variants
  'print:bg-white': 'print:bg-base-100',
  'print:text-black': 'print:text-base-content',
  'print:bg-gray-100': 'print:bg-base-200',
  'print:bg-gray-50': 'print:bg-base-200',
  'print:border-gray-200': 'print:border-base-content/20',
  'print:text-gray-400': 'print:text-base-content/60',
  'print:text-gray-500': 'print:text-base-content/60',
  'print:text-gray-600': 'print:text-base-content/70',
  'print:text-gray-700': 'print:text-base-content/80',
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
        // We use string boundaries to avoid replacing parts of a word
        const regex = new RegExp(`(?<=[\\s"'\\\`])` + key.replace(/\//g, '\\/') + `(?=[\\s"'\\\`])`, 'g');
        content = content.replace(regex, value);
      }

      if (content !== originalContent) {
        fs.writeFileSync(fullPath, content, 'utf8');
        console.log(`Updated: ${fullPath}`);
      }
    }
  }
}

processDirectory('/Users/uttamkumar_barik/Documents/Antigravity/java/finsight-frontend/src');
