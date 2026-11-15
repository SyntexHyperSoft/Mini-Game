#!/usr/bin/env python3
import re
import sys

with open('app/src/main/assets/game/index.html', 'r', encoding='utf-8') as f:
    content = f.read()

print('Original length:', len(content))

# 1. Update CSS: Add indicator dot, custom dialog, redesigned star enchant modal, and xp-bar-text-row
css_insert = """
/* === NOTIFICATION BADGES / ACTION AVAILABLE INDICATOR === */
.has-indicator { position: relative !important; }
.indicator-dot {
  position: absolute;
  top: -3px;
  right: -3px;
  width: 11px;
  height: 11px;
  background: #00ff88;
  border: 2px solid #030308;
  border-radius: 50%;
  box-shadow: 0 0 8px #00ff88, 0 0 16px rgba(0,255,136,0.8);
  animation: indicatorPulse 1.4s infinite ease-in-out;
  pointer-events: none;
  z-index: 25;
}
@keyframes indicatorPulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.35); opacity: 0.85; box-shadow: 0 0 14px #00ff88, 0 0 24px #00ff88; }
}

.xp-bar-text-row {
  position: relative;
  z-index: 2;
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 10px;
  box-sizing: border-box;
  font-size: 9px;
  font-weight: 800;
  color: #fff;
  letter-spacing: 0.8px;
  text-shadow: 0 0 4px rgba(0,0,0,0.9);
}

/* === CUSTOM SCI-FI GAME DIALOG === */
#custom-game-dialog {
  position: fixed;
  inset: 0;
  background: rgba(3, 5, 12, 0.85);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  z-index: 10000;
  display: none;
  align-items: center;
  justify-content: center;
  padding: 20px;
  opacity: 0;
  transition: opacity 0.25s ease;
}
#custom-game-dialog.show {
  display: flex;
  opacity: 1;
}
.dialog-box {
  background: linear-gradient(145deg, rgba(10, 15, 30, 0.98), rgba(5, 8, 18, 0.98));
  border: 1px solid rgba(0, 255, 255, 0.4);
  box-shadow: 0 0 40px rgba(0, 255, 255, 0.2), inset 0 0 25px rgba(0, 255, 255, 0.05);
  border-radius: 18px;
  width: 100%;
  max-width: 380px;
  padding: 22px 20px 18px;
  text-align: center;
  position: relative;
  transform: scale(0.92);
  transition: transform 0.25s cubic-bezier(0.2, 0.8, 0.2, 1);
}
#custom-game-dialog.show .dialog-box {
  transform: scale(1);
}
.dialog-header {
  font-family: 'Unbounded', sans-serif;
  font-size: 15px;
  font-weight: 900;
  color: #00ffff;
  letter-spacing: 1.5px;
  margin-bottom: 12px;
  text-shadow: 0 0 10px rgba(0,255,255,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.dialog-message {
  font-size: 13px;
  color: #e2e8f0;
  line-height: 1.5;
  margin-bottom: 16px;
  word-break: break-word;
}
.dialog-input {
  width: 100%;
  padding: 12px 14px;
  background: rgba(0, 0, 0, 0.6);
  border: 1px solid rgba(0, 255, 255, 0.4);
  border-radius: 10px;
  color: #fff;
  font-family: 'Unbounded', sans-serif;
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
  margin-bottom: 16px;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.dialog-input:focus {
  border-color: #00ffff;
  box-shadow: 0 0 15px rgba(0, 255, 255, 0.4);
}
.dialog-btn-row {
  display: flex;
  gap: 10px;
  margin-top: 4px;
}

/* === REDESIGNED STAR ENCHANTMENT MODAL & CARDS === */
#star-enchant-modal .modal-window {
  border-color: rgba(255, 215, 0, 0.4);
  background: linear-gradient(180deg, rgba(15, 12, 30, 0.98), rgba(6, 4, 18, 0.98));
  box-shadow: 0 0 50px rgba(255, 215, 0, 0.25), inset 0 0 30px rgba(168, 85, 247, 0.15);
}
.star-enchant-tier-card {
  background: linear-gradient(135deg, rgba(255,255,255,0.03), rgba(0,0,0,0.5));
  border: 1px solid rgba(255,255,255,0.12);
  border-radius: 14px;
  padding: 14px;
  margin-bottom: 12px;
  position: relative;
  overflow: hidden;
  transition: all 0.25s ease;
}
.star-enchant-tier-card.tier-gold {
  border-color: rgba(255, 215, 0, 0.45);
  box-shadow: 0 0 20px rgba(255, 215, 0, 0.1), inset 0 0 20px rgba(255, 215, 0, 0.04);
}
.star-enchant-tier-card.tier-galaxy {
  border-color: rgba(192, 132, 252, 0.5);
  box-shadow: 0 0 25px rgba(192, 132, 252, 0.15), inset 0 0 20px rgba(0, 240, 255, 0.05);
}
.enchant-svg-gold {
  filter: drop-shadow(0 0 12px #ffd700);
  animation: goldPulse 3s infinite alternate ease-in-out;
}
.enchant-svg-galaxy {
  filter: drop-shadow(0 0 15px #c084fc);
  animation: galaxyPulse 4s infinite linear;
}
"""

content = content.replace('</style>', css_insert + '\n</style>', 1)
print('CSS inserted')

with open('app/src/main/assets/game/index.html', 'w', encoding='utf-8') as f:
    f.write(content)
