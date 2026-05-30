#!/usr/bin/env bash
set -euo pipefail

# ─── LyfeGame Production Server Setup ──────────────────────────────────────
# Run as root on a fresh Ubuntu 22.04/24.04 Digital Ocean droplet:
#   curl -sL <raw-github-url>/setup-server.sh | bash
# Or: scp this file to server and run: sudo bash setup-server.sh

echo "==> Updating system packages"
apt-get update -qq && apt-get upgrade -y -qq

# ─── Java 17 ────────────────────────────────────────────────────────────────
echo "==> Installing Java 17"
apt-get install -y -qq openjdk-17-jre-headless

# ─── MongoDB 7 ──────────────────────────────────────────────────────────────
echo "==> Installing MongoDB 7"
apt-get install -y -qq gnupg curl
curl -fsSL https://www.mongodb.org/static/pgp/server-7.0.asc | gpg --dearmor -o /usr/share/keyrings/mongodb-server-7.0.gpg

# Detect Ubuntu version
UBUNTU_CODENAME=$(lsb_release -cs)
echo "deb [ signed-by=/usr/share/keyrings/mongodb-server-7.0.gpg ] https://repo.mongodb.org/apt/ubuntu ${UBUNTU_CODENAME}/mongodb-org/7.0 multiverse" | tee /etc/apt/sources.list.d/mongodb-org-7.0.list
apt-get update -qq
apt-get install -y -qq mongodb-org

systemctl enable mongod
systemctl start mongod

# ─── nginx ──────────────────────────────────────────────────────────────────
echo "==> Installing nginx"
apt-get install -y -qq nginx

# ─── Create app user and directories ────────────────────────────────────────
echo "==> Setting up app directories"
useradd -r -s /usr/sbin/nologin lyfegame 2>/dev/null || true
mkdir -p /opt/lyfegame/server
mkdir -p /opt/lyfegame/client
chown -R lyfegame:lyfegame /opt/lyfegame

# ─── systemd service for Spring Boot ────────────────────────────────────────
echo "==> Creating systemd service"
cat > /etc/systemd/system/lyfegame-server.service << 'EOF'
[Unit]
Description=LyfeGame Spring Boot Server
After=network.target mongod.service
Requires=mongod.service

[Service]
Type=simple
User=lyfegame
Group=lyfegame
WorkingDirectory=/opt/lyfegame/server

ExecStart=/usr/bin/java -Xmx512m -Xms256m \
  -jar /opt/lyfegame/server/lyfe-game-server-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod

Restart=always
RestartSec=5
StandardOutput=journal
StandardError=journal

# Environment variables — edit these
Environment=MONGODB_URI=mongodb://localhost:27017/lyfe
Environment=MISTRAL_API_KEY=
Environment=CORS_ORIGINS=http://localhost:4200

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable lyfegame-server

# ─── nginx config ───────────────────────────────────────────────────────────
echo "==> Configuring nginx"
cat > /etc/nginx/sites-available/lyfegame << 'NGINX'
server {
    listen 80;
    server_name _;

    root /opt/lyfegame/client;
    index index.html;

    # Angular SPA — serve index.html for all non-file routes
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API proxy
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket proxy
    location /ws/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 86400;
    }

    # Gzip
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml text/javascript;
    gzip_min_length 256;
}
NGINX

ln -sf /etc/nginx/sites-available/lyfegame /etc/nginx/sites-enabled/lyfegame
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl restart nginx

# ─── Firewall ───────────────────────────────────────────────────────────────
echo "==> Configuring firewall"
ufw allow OpenSSH
ufw allow 'Nginx Full'
ufw --force enable

# ─── Swap (helpful for 2GB RAM) ────────────────────────────────────────────
echo "==> Setting up 2GB swap"
if [ ! -f /swapfile ]; then
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  Server setup complete!"
echo "═══════════════════════════════════════════════════════════"
echo ""
echo "  Next steps:"
echo "  1. Set your Mistral API key:"
echo "     sudo systemctl edit lyfegame-server"
echo "     Add: Environment=MISTRAL_API_KEY=your-key-here"
echo ""
echo "  2. Add GitHub Actions secrets to BOTH repos:"
echo "     SERVER_IP       = your droplet IP"
echo "     SERVER_USER     = root (or a deploy user)"
echo "     SSH_PRIVATE_KEY = contents of your SSH private key"
echo ""
echo "  3. Push to main on both repos to trigger deployment"
echo ""
echo "  4. Or deploy manually:"
echo "     scp server.jar root@IP:/opt/lyfegame/server/"
echo "     sudo systemctl restart lyfegame-server"
echo ""
echo "  Logs:  journalctl -u lyfegame-server -f"
echo "  Mongo: mongosh"
echo "═══════════════════════════════════════════════════════════"
