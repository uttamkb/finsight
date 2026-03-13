#!/bin/bash
echo "TestSprite Wrapper starting at $(date)" > /tmp/testsprite-mcp-init.log
export npm_config_cache=/tmp/testsprite-custom-cache
export API_KEY="sk-user-IYsbyAz2ZOmDbEaP7CBLsqAwyAYcK_SpNqa9T43LzJgxGdfptdOtHoQw4V9vp7rqvoy-XseuitrhwfeIFBnWpFziINrJowkkkub0T0ZO7jFGTU-u6_xZnLHbMm3ba9Xt8cE"
exec /opt/homebrew/bin/npx -y @testsprite/testsprite-mcp@latest 2>/tmp/testsprite-mcp-error.log
