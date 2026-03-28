const fs = require('fs');
const path = require('path');

const mappings = {
  'bg-background': 'bg-base-100',
  'bg-card': 'bg-base-200',
  'text-foreground': 'text-base-content',
  'text-muted-foreground': 'text-base-content/60',
  'border-border': 'border-base-content/20',
  'bg-muted': 'bg-base-300',
  'text-muted': 'text-base-content/60',
  'border-primary/20': 'border-primary/30',
  'bg-secondary/50': 'bg-secondary/20',
  'bg-primary/10': 'bg-primary/20'
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
        const regex = new RegExp(`\\b${key}\\b`, 'g');
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
