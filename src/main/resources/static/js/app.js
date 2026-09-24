/**
 * MarketScout - Real-Time Commodity Terminal
 * WebSocket Client (SockJS + STOMP) & Chart.js Integration
 */

// Commodity Metadata Configuration
const COMMODITY_CONFIG = {
    GOLD: {
        name: 'GOLD',
        code: 'XAU',
        unit: 'USD/oz',
        color: '#eab308',
        borderColor: '#facc15',
        bgColor: 'rgba(234, 179, 8, 0.12)',
        decimals: 2
    },
    SILVER: {
        name: 'SILVER',
        code: 'XAG',
        unit: 'USD/oz',
        color: '#94a3b8',
        borderColor: '#cbd5e1',
        bgColor: 'rgba(148, 163, 184, 0.12)',
        decimals: 2
    },
    COFFEE: {
        name: 'COFFEE',
        code: 'KC',
        unit: 'USD/lb',
        color: '#f97316',
        borderColor: '#fb923c',
        bgColor: 'rgba(249, 115, 22, 0.12)',
        decimals: 2
    },
    CRUDE_OIL: {
        name: 'CRUDE OIL',
        code: 'CL',
        unit: 'USD/bbl',
        color: '#06b6d4',
        borderColor: '#22d3ee',
        bgColor: 'rgba(6, 182, 212, 0.12)',
        decimals: 2
    }
};

let activeCommodity = 'GOLD';
let stompClient = null;
let marketChart = null;
let latestPricesMap = {};

// Initialize on DOM Ready
document.addEventListener('DOMContentLoaded', () => {
    initClock();
    initChart();
    initAlertForm();
    loadInitialPrices();
    loadInitialHistory(activeCommodity);
    fetchActiveAlerts();
    connectWebSocket();
    initDesktopApp();
});

// Digital Server Clock
function initClock() {
    const clockEl = document.getElementById('server-clock');
    const update = () => {
        const now = new Date();
        clockEl.textContent = now.toLocaleTimeString('en-US', { hour12: false });
    };
    update();
    setInterval(update, 1000);
}

// Chart.js Setup
function initChart() {
    const ctx = document.getElementById('marketChart').getContext('2d');
    const cfg = COMMODITY_CONFIG[activeCommodity];

    marketChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: `${cfg.name} (${cfg.code})`,
                data: [],
                borderColor: cfg.borderColor,
                backgroundColor: cfg.bgColor,
                borderWidth: 2.2,
                pointRadius: 0,
                pointHoverRadius: 5,
                pointHoverBackgroundColor: cfg.borderColor,
                pointHoverBorderColor: '#ffffff',
                pointHoverBorderWidth: 2,
                fill: true,
                tension: 0.35
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: {
                duration: 400
            },
            interaction: {
                intersect: false,
                mode: 'index'
            },
            plugins: {
                legend: {
                    display: false
                },
                tooltip: {
                    backgroundColor: 'rgba(15, 23, 42, 0.95)',
                    titleColor: '#94a3b8',
                    bodyColor: '#ffffff',
                    bodyFont: {
                        family: 'JetBrains Mono',
                        weight: 'bold'
                    },
                    borderColor: 'rgba(51, 65, 85, 0.8)',
                    borderWidth: 1,
                    padding: 10,
                    displayColors: false,
                    callbacks: {
                        label: function (context) {
                            const val = context.parsed.y;
                            return `Price: $${formatNumber(val, cfg.decimals)} ${cfg.unit}`;
                        }
                    }
                }
            },
            scales: {
                x: {
                    grid: {
                        color: 'rgba(255, 255, 255, 0.04)',
                        drawBorder: false
                    },
                    ticks: {
                        color: '#64748b',
                        font: {
                            family: 'JetBrains Mono',
                            size: 10
                        },
                        maxRotation: 0,
                        autoSkip: true,
                        maxTicksLimit: 8
                    }
                },
                y: {
                    position: 'right',
                    grid: {
                        color: 'rgba(255, 255, 255, 0.05)',
                        drawBorder: false
                    },
                    ticks: {
                        color: '#94a3b8',
                        font: {
                            family: 'JetBrains Mono',
                            size: 11
                        },
                        callback: function (val) {
                            return '$' + formatNumber(val, cfg.decimals);
                        }
                    }
                }
            }
        }
    });
}

// Select Commodity Tab and Re-render Chart
function selectCommodity(commodityKey) {
    if (!COMMODITY_CONFIG[commodityKey]) return;
    activeCommodity = commodityKey;
    const cfg = COMMODITY_CONFIG[activeCommodity];

    // Update active tab buttons
    ['GOLD', 'SILVER', 'COFFEE', 'CRUDE_OIL'].forEach(k => {
        const tab = document.getElementById(`tab-${k}`);
        const card = document.getElementById(`card-${k}`);
        if (k === activeCommodity) {
            tab.className = `px-3 py-1.5 rounded-md transition font-medium bg-${getTailwindColor(k)}/20 text-${getTailwindColor(k)} border border-${getTailwindColor(k)}/40`;
            card.classList.add('ring-2', 'ring-emerald-500/50');
        } else {
            tab.className = 'px-3 py-1.5 rounded-md transition font-medium text-slate-400 hover:text-slate-200';
            card.classList.remove('ring-2', 'ring-emerald-500/50');
        }
    });

    // Update Chart header
    document.getElementById('active-commodity-name').textContent = cfg.name;
    document.getElementById('active-commodity-code').textContent = cfg.code;
    document.getElementById('active-commodity-unit').textContent = cfg.unit;

    if (latestPricesMap[activeCommodity]) {
        document.getElementById('active-commodity-price').textContent = `$${formatNumber(latestPricesMap[activeCommodity].currentPrice, cfg.decimals)}`;
    }

    // Update Chart styles & dataset
    marketChart.data.datasets[0].label = `${cfg.name} (${cfg.code})`;
    marketChart.data.datasets[0].borderColor = cfg.borderColor;
    marketChart.data.datasets[0].backgroundColor = cfg.bgColor;
    marketChart.data.datasets[0].pointHoverBackgroundColor = cfg.borderColor;

    // Load price history from backend
    loadInitialHistory(activeCommodity);

    // Sync select dropdown in form
    const sel = document.getElementById('alert-commodity');
    if (sel) sel.value = activeCommodity;
    updateSuggestedPrice();
}

function getTailwindColor(commodity) {
    switch (commodity) {
        case 'GOLD': return 'amber-400';
        case 'SILVER': return 'slate-300';
        case 'COFFEE': return 'orange-400';
        case 'CRUDE_OIL': return 'cyan-400';
        default: return 'emerald-400';
    }
}

// WebSocket Connection (SockJS + STOMP)
function connectWebSocket() {
    const statusPill = document.getElementById('connection-status');
    const indicator = document.getElementById('connection-indicator');

    statusPill.textContent = 'Connecting to /ws-market...';
    indicator.className = 'w-2.5 h-2.5 rounded-full bg-amber-400 animate-pulse';

    const socket = new SockJS('/ws-market');
    stompClient = Stomp.over(socket);
    stompClient.debug = null; // Quiet console in production

    stompClient.connect({}, function (frame) {
        statusPill.textContent = 'CONNECTED (/ws-market)';
        indicator.className = 'w-2.5 h-2.5 rounded-full bg-emerald-400 shadow-[0_0_8px_#10b981]';

        // 1. Subscribe to real-time price updates
        stompClient.subscribe('/topic/prices', function (message) {
            try {
                const price = JSON.parse(message.body);
                handlePriceTick(price);
            } catch (err) {
                console.error('Error handling price tick:', err);
            }
        });

        // 2. Subscribe to threshold alert notifications
        stompClient.subscribe('/topic/alerts', function (message) {
            try {
                const alertNotification = JSON.parse(message.body);
                handleAlertNotification(alertNotification);
            } catch (err) {
                console.error('Error handling alert notification:', err);
            }
        });

    }, function (error) {
        statusPill.textContent = 'DISCONNECTED (Retrying in 5s...)';
        indicator.className = 'w-2.5 h-2.5 rounded-full bg-rose-500 shadow-[0_0_8px_#f43f5e]';
        setTimeout(connectWebSocket, 5000);
    });
}

// Handle Real-time Price Tick
function handlePriceTick(price) {
    const commodity = price.commodity;
    const cfg = COMMODITY_CONFIG[commodity];
    if (!cfg) return;

    const oldPriceData = latestPricesMap[commodity];
    latestPricesMap[commodity] = price;

    // Update Ticker Card UI
    const priceEl = document.getElementById(`price-${commodity}`);
    const badgeEl = document.getElementById(`badge-${commodity}`);
    const cardEl = document.getElementById(`card-${commodity}`);

    if (priceEl) {
        priceEl.textContent = `$${formatNumber(price.currentPrice, cfg.decimals)}`;
    }

    if (badgeEl) {
        const pct = price.changePercent;
        const sign = pct >= 0 ? '+' : '';
        badgeEl.textContent = `${sign}${pct.toFixed(2)}%`;

        if (pct > 0) {
            badgeEl.className = 'text-xs font-mono font-medium px-2 py-0.5 rounded bg-emerald-950/80 text-emerald-400 border border-emerald-800/60';
        } else if (pct < 0) {
            badgeEl.className = 'text-xs font-mono font-medium px-2 py-0.5 rounded bg-rose-950/80 text-rose-400 border border-rose-800/60';
        } else {
            badgeEl.className = 'text-xs font-mono font-medium px-2 py-0.5 rounded bg-slate-800 text-slate-400 border border-slate-700';
        }
    }

    // Flash border/background animation
    if (cardEl && oldPriceData) {
        const isUp = price.currentPrice >= oldPriceData.currentPrice;
        cardEl.classList.remove('flash-up', 'flash-down');
        void cardEl.offsetWidth; // trigger reflow
        cardEl.classList.add(isUp ? 'flash-up' : 'flash-down');
    }

    // If this price update belongs to the active chart commodity, append to chart
    if (commodity === activeCommodity) {
        const activePriceEl = document.getElementById('active-commodity-price');
        if (activePriceEl) {
            activePriceEl.textContent = `$${formatNumber(price.currentPrice, cfg.decimals)}`;
        }

        const timeLabel = formatTimestamp(price.timestamp);

        marketChart.data.labels.push(timeLabel);
        marketChart.data.datasets[0].data.push(price.currentPrice);

        // Keep rolling 50 data points on chart
        while (marketChart.data.labels.length > 50) {
            marketChart.data.labels.shift();
            marketChart.data.datasets[0].data.shift();
        }

        marketChart.update('none'); // smooth 60fps update
    }

    // Update suggested price if form is pointing to this commodity
    const formCommodity = document.getElementById('alert-commodity');
    if (formCommodity && formCommodity.value === commodity) {
        updateSuggestedPrice();
    }
}

// Handle Alert Notification
function handleAlertNotification(alert) {
    // 0. Audio Alert Chime
    playAlertSound();

    // 1. Show Toast Banner
    showToastAlert(alert);

    // 2. Prepend to Terminal Alert Log
    appendAlertLog(alert);

    // 3. Refresh Active Alerts list (since triggered alert is no longer active)
    fetchActiveAlerts();
}

// Render Toast Banner
function showToastAlert(alert) {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = 'pointer-events-auto bg-slate-900 border-2 border-rose-500/80 text-white p-4 rounded-xl shadow-2xl shadow-rose-950/60 flex items-start gap-3 transition-all duration-300 transform translate-x-full';

    const cfg = COMMODITY_CONFIG[alert.commodity] || { code: alert.commodity, unit: '' };

    toast.innerHTML = `
        <div class="p-1.5 rounded-lg bg-rose-500/20 text-rose-400 mt-0.5">
            <svg class="w-5 h-5 animate-pulse" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
            </svg>
        </div>
        <div class="flex-1">
            <div class="flex items-center justify-between">
                <span class="font-bold text-xs uppercase tracking-wide text-rose-400">ALERT BREACH DETECTED</span>
                <span class="text-[10px] font-mono text-slate-400">${formatTimestamp(alert.timestamp)}</span>
            </div>
            <p class="text-xs text-slate-200 mt-1 font-mono">${alert.message}</p>
            <div class="mt-2 text-[11px] font-mono text-slate-400">
                Triggered: <span class="text-white font-bold">$${alert.triggeredPrice.toFixed(2)}</span> | Target: <span class="text-slate-300">$${alert.targetPrice.toFixed(2)}</span>
            </div>
        </div>
        <button onclick="this.parentElement.remove()" class="text-slate-400 hover:text-white">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
            </svg>
        </button>
    `;

    container.appendChild(toast);

    // Slide in
    setTimeout(() => {
        toast.classList.remove('translate-x-full');
    }, 10);

    // Auto dismiss after 7 seconds
    setTimeout(() => {
        toast.classList.add('translate-x-full', 'opacity-0');
        setTimeout(() => toast.remove(), 300);
    }, 7000);
}

// Append to Alert Log Section
function appendAlertLog(alert) {
    const container = document.getElementById('alert-logs-container');
    const placeholder = document.getElementById('empty-logs-placeholder');
    if (placeholder) placeholder.style.display = 'none';

    const logRow = document.createElement('div');
    logRow.className = 'flex flex-wrap items-center justify-between gap-2 p-2.5 rounded-lg bg-slate-900/90 border border-rose-900/40 hover:border-rose-700/60 transition';

    const time = formatTimestamp(alert.timestamp);
    const cfg = COMMODITY_CONFIG[alert.commodity] || { code: alert.commodity, unit: '' };

    logRow.innerHTML = `
        <div class="flex items-center gap-3">
            <span class="text-slate-500 font-mono text-[11px]">[${time}]</span>
            <span class="px-2 py-0.5 rounded font-bold text-[11px] bg-rose-500/20 text-rose-300 border border-rose-500/30">${alert.commodity}</span>
            <span class="text-slate-200 text-xs">${alert.message}</span>
        </div>
        <div class="flex items-center gap-3 text-[11px] font-mono">
            <span class="text-slate-400">Triggered: <span class="text-emerald-400 font-bold">$${alert.triggeredPrice.toFixed(2)}</span></span>
            <span class="text-slate-500">Target: $${alert.targetPrice.toFixed(2)}</span>
        </div>
    `;

    container.insertBefore(logRow, container.firstChild);
}

function clearNotificationLog() {
    const container = document.getElementById('alert-logs-container');
    container.innerHTML = `
        <div id="empty-logs-placeholder" class="text-slate-500 text-center py-6 font-mono text-xs">
            Listening on WebSocket STOMP broker... Triggered notifications will stream here in real time.
        </div>
    `;
}

// REST: Load Initial Prices
function loadInitialPrices() {
    fetch('/api/commodities')
        .then(res => res.json())
        .then(prices => {
            if (!Array.isArray(prices)) return;
            prices.forEach(p => {
                latestPricesMap[p.commodity] = p;
                handlePriceTick(p);
            });
            updateSuggestedPrice();
        })
        .catch(err => console.error('Failed to load initial prices:', err));
}

// REST: Load Historical Prices for Active Commodity
function loadInitialHistory(commodity) {
    const cfg = COMMODITY_CONFIG[commodity];
    if (!cfg) return;

    fetch(`/api/commodities/${cfg.code}/history`)
        .then(res => res.json())
        .then(history => {
            if (!Array.isArray(history) || history.length === 0) return;

            marketChart.data.labels = history.map(item => formatTimestamp(item.timestamp));
            marketChart.data.datasets[0].data = history.map(item => item.currentPrice);
            marketChart.update();
        })
        .catch(err => console.error(`Failed to load history for ${commodity}:`, err));
}

// REST: Fetch Active Alerts
function fetchActiveAlerts() {
    fetch('/api/alerts')
        .then(res => res.json())
        .then(rules => {
            renderActiveRules(rules);
        })
        .catch(err => console.error('Failed to fetch active alerts:', err));
}

function renderActiveRules(rules) {
    const container = document.getElementById('active-rules-container');
    const countEl = document.getElementById('active-alert-count');
    if (!container) return;

    if (countEl) countEl.textContent = rules ? rules.length : 0;

    if (!rules || rules.length === 0) {
        container.innerHTML = `<div class="text-center py-6 text-slate-500 text-xs font-mono">No active alert triggers</div>`;
        return;
    }

    container.innerHTML = rules.map(rule => {
        const isGte = rule.condition === 'GREATER_THAN_OR_EQUAL';
        const condSymbol = isGte ? '≥' : '≤';
        const condColor = isGte ? 'text-emerald-400 bg-emerald-500/10 border-emerald-500/30' : 'text-rose-400 bg-rose-500/10 border-rose-500/30';

        return `
            <div class="flex items-center justify-between p-2.5 rounded-lg bg-slate-900 border border-slate-800 text-xs font-mono hover:border-slate-700 transition">
                <div class="flex items-center gap-2">
                    <span class="font-bold text-white">${rule.commodity}</span>
                    <span class="px-1.5 py-0.5 rounded text-[10px] font-semibold border ${condColor}">${condSymbol}</span>
                    <span class="text-slate-300 font-bold">$${rule.targetPrice.toFixed(2)}</span>
                </div>
                <button onclick="deleteAlertRule('${rule.id}')" title="Delete alert" class="text-slate-500 hover:text-rose-400 transition p-1">
                    <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
                    </svg>
                </button>
            </div>
        `;
    }).join('');
}

// REST: Delete Alert Rule
function deleteAlertRule(id) {
    fetch(`/api/alerts/${id}`, {
        method: 'DELETE'
    })
    .then(res => {
        if (res.ok) {
            fetchActiveAlerts();
        }
    })
    .catch(err => console.error('Error deleting alert rule:', err));
}

// Alert Form Wiring
function initAlertForm() {
    const form = document.getElementById('alert-form');
    const commoditySelect = document.getElementById('alert-commodity');

    if (commoditySelect) {
        commoditySelect.addEventListener('change', updateSuggestedPrice);
    }

    if (form) {
        form.addEventListener('submit', function (e) {
            e.preventDefault();

            const commodity = document.getElementById('alert-commodity').value;
            const condition = document.getElementById('alert-condition').value;
            const targetPrice = parseFloat(document.getElementById('alert-target-price').value);

            if (!commodity || !condition || isNaN(targetPrice)) {
                alert('Please fill out all alert parameters.');
                return;
            }

            const btn = document.getElementById('btn-create-alert');
            const originalContent = btn.innerHTML;
            btn.disabled = true;
            btn.innerHTML = `<span class="animate-spin mr-1">⌛</span> Arming...`;

            fetch('/api/alerts', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    commodity: commodity,
                    condition: condition,
                    targetPrice: targetPrice
                })
            })
            .then(res => {
                if (!res.ok) throw new Error('Failed to create alert rule');
                return res.json();
            })
            .then(createdRule => {
                fetchActiveAlerts();
                document.getElementById('alert-target-price').value = '';
                btn.innerHTML = `<span class="text-emerald-300">✓ Trigger Armed!</span>`;
                setTimeout(() => {
                    btn.disabled = false;
                    btn.innerHTML = originalContent;
                }, 1500);
            })
            .catch(err => {
                console.error(err);
                btn.disabled = false;
                btn.innerHTML = originalContent;
                alert('Failed to arm alert trigger. See console for details.');
            });
        });
    }
}

function updateSuggestedPrice() {
    const sel = document.getElementById('alert-commodity');
    const label = document.getElementById('suggested-price-label');
    if (!sel || !label) return;

    const commodity = sel.value;
    const current = latestPricesMap[commodity];
    if (current) {
        label.textContent = `Current: $${formatNumber(current.currentPrice, 2)}`;
    }
}

// Helpers
function formatNumber(val, decimals = 2) {
    if (val === undefined || val === null || isNaN(val)) return '0.00';
    return Number(val).toLocaleString('en-US', {
        minimumFractionDigits: decimals,
        maximumFractionDigits: decimals
    });
}

function formatTimestamp(isoString) {
    if (!isoString) return '--:--:--';
    const date = new Date(isoString);
    return date.toTimeString().split(' ')[0];
}

// ───────────────────────────────────────────────────────────
//  Desktop Application Features & Window Controls
// ───────────────────────────────────────────────────────────
let audioAlertsEnabled = true;
let audioCtx = null;

function toggleAudioAlerts() {
    audioAlertsEnabled = !audioAlertsEnabled;
    const onIcon = document.getElementById('icon-sound-on');
    const offIcon = document.getElementById('icon-sound-off');
    if (onIcon && offIcon) {
        onIcon.classList.toggle('hidden', !audioAlertsEnabled);
        offIcon.classList.toggle('hidden', audioAlertsEnabled);
    }
}

function playAlertSound() {
    if (!audioAlertsEnabled) return;
    try {
        if (!audioCtx) {
            audioCtx = new (window.AudioContext || window.webkitAudioContext)();
        }
        if (audioCtx.state === 'suspended') {
            audioCtx.resume();
        }
        const osc = audioCtx.createOscillator();
        const gain = audioCtx.createGain();
        osc.type = 'triangle';
        osc.frequency.setValueAtTime(880, audioCtx.currentTime);
        osc.frequency.exponentialRampToValueAtTime(440, audioCtx.currentTime + 0.22);
        gain.gain.setValueAtTime(0.12, audioCtx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + 0.22);
        osc.connect(gain);
        gain.connect(audioCtx.destination);
        osc.start();
        osc.stop(audioCtx.currentTime + 0.22);
    } catch (e) {
        // AudioContext initialization postponed until user interaction
    }
}

function toggleFullscreen() {
    if (!document.fullscreenElement) {
        document.documentElement.requestFullscreen().catch(err => {
            console.warn("Fullscreen request error:", err);
        });
    } else {
        if (document.exitFullscreen) {
            document.exitFullscreen();
        }
    }
}

function promptQuitApp() {
    const modal = document.getElementById('desktop-exit-modal');
    if (modal) {
        modal.classList.remove('hidden');
        modal.classList.add('flex');
    }
}

function closeQuitPrompt() {
    const modal = document.getElementById('desktop-exit-modal');
    if (modal) {
        modal.classList.add('hidden');
        modal.classList.remove('flex');
    }
}

function confirmExitApp() {
    const btn = document.getElementById('btn-confirm-exit');
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = `<span class="animate-pulse">Shutting Down Server...</span>`;
    }
    fetch('/api/app/shutdown', { method: 'POST' })
        .then(() => {
            setTimeout(() => {
                window.close();
            }, 300);
        })
        .catch(() => {
            window.close();
        });
}

function initDesktopApp() {
    // Keyboard Hotkeys
    window.addEventListener('keydown', (e) => {
        if (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT' || e.target.tagName === 'TEXTAREA') {
            return;
        }

        if (e.key === '1') {
            selectCommodity('GOLD');
        } else if (e.key === '2') {
            selectCommodity('SILVER');
        } else if (e.key === '3') {
            selectCommodity('COFFEE');
        } else if (e.key === '4') {
            selectCommodity('CRUDE_OIL');
        } else if (e.key === 'F11') {
            e.preventDefault();
            toggleFullscreen();
        } else if (e.ctrlKey && (e.key === 'q' || e.key === 'Q')) {
            e.preventDefault();
            promptQuitApp();
        } else if (e.key === 'Escape') {
            closeQuitPrompt();
        }
    });

    // Poll Desktop App Health / Metrics
    const updateAppStats = () => {
        fetch('/api/app/info')
            .then(res => res.json())
            .then(data => {
                const ramEl = document.getElementById('desktop-ram-badge');
                if (ramEl && data.usedMemoryMb !== undefined) {
                    ramEl.textContent = `RAM: ${data.usedMemoryMb} MB`;
                }
                const uptimeEl = document.getElementById('footer-uptime');
                if (uptimeEl && data.uptimeSeconds !== undefined) {
                    const hrs = Math.floor(data.uptimeSeconds / 3600);
                    const mins = Math.floor((data.uptimeSeconds % 3600) / 60);
                    const secs = data.uptimeSeconds % 60;
                    uptimeEl.textContent = `UPTIME: ${hrs}h ${mins}m ${secs}s`;
                }
            })
            .catch(() => {});
    };

    updateAppStats();
    setInterval(updateAppStats, 5000);
}
