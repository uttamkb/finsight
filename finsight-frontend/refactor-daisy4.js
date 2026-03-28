const fs = require('fs');
const path = require('path');

const mappings = {
  // Map standard shadcn-style foreground names to DaisyUI content names
  'text-primary-foreground': 'text-primary-content',
  'text-secondary-foreground': 'text-secondary-content',
  'text-accent-foreground': 'text-accent-content',
  'text-destructive-foreground': 'text-error-content',
  'text-error-foreground': 'text-error-content',
  'text-success-foreground': 'text-success-content',
  'text-warning-foreground': 'text-warning-content',
  'text-info-foreground': 'text-info-content',
  
  // Also map common utility pattern to the DaisyUI "content" suffix
  'text-primary-content': 'text-primary-content', // ensure consistency
  
  // Fix specific button patterns that were using shadcn-like inline flex strings
  // Statements / Dashboard / Settings buttons are the primary offenders
  'bg-primary text-primary-foreground': 'btn btn-primary btn-sm text-primary-content',
  'bg-secondary text-secondary-foreground': 'btn btn-secondary btn-sm text-secondary-content',
  'bg-accent text-accent-foreground': 'btn btn-accent btn-sm text-accent-content'
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
      
      // Perform direct string replacements for the clusters
      for (const [key, value] of Object.entries(mappings)) {
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
