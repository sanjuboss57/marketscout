/**
 * MarketScout - Real-Time Multi-Asset Terminal & Paper Trading Platform
 * Supports: Commodities, Cryptocurrencies, Equities (Stocks)
 * WebSocket Client (SockJS + STOMP) & Chart.js Integration
 */

// Comprehensive Financial Instrument Configuration (10 Assets)
const COMMODITY_CONFIG = {
    GOLD: {
        name: 'GOLD',
        code: 'XAU',
        category: 'COMMODITY',
        unit: 'USD/oz',
        color: '#eab308',
        borderColor: '#facc15',
        bgColor: 'rgba(234, 179, 8, 0.15)',
        decimals: 2
    },
    SILVER: {
        name: 'SILVER',
        code: 'XAG',
        category: 'COMMODITY',
        unit: 'USD/oz',
        color: '#94a3b8',
        borderColor: '#cbd5e1',
        bgColor: 'rgba(148, 163, 184, 0.15)',
        decimals: 2
    },
    COFFEE: {
        name: 'COFFEE',
        code: 'KC',
        category: 'COMMODITY',
        unit: 'USD/lb',
        color: '#f97316',
        borderColor: '#fb923c',
        bgColor: 'rgba(249, 115, 22, 0.15)',
        decimals: 2
    },
    CRUDE_OIL: {
        name: 'CRUDE OIL',
        code: 'CL',
        category: 'COMMODITY',
        unit: 'USD/bbl',
        color: '#06b6d4',
        borderColor: '#22d3ee',
        bgColor: 'rgba(6, 182, 212, 0.15)',
        decimals: 2
    },
    BITCOIN: {
        name: 'BITCOIN',
        code: 'BTC',
        category: 'CRYPTO',
        unit: 'USD',
        color: '#f59e0b',
        borderColor: '#fbbf24',
        bgColor: 'rgba(245, 158, 11, 0.15)',
        decimals: 2
    },
    ETHEREUM: {
        name: 'ETHEREUM',
        code: 'ETH',
        category: 'CRYPTO',
        unit: 'USD',
        color: '#8b5cf6',
        borderColor: '#a78bfa',
        bgColor: 'rgba(139, 92, 246, 0.15)',
        decimals: 2
    },
    SOLANA: {
        name: 'SOLANA',
        code: 'SOL',
        category: 'CRYPTO',
        unit: 'USD',
        color: '#14b8a6',
        borderColor: '#2dd4bf',
        bgColor: 'rgba(20, 184, 166, 0.15)',
        decimals: 2
    },
    APPLE: {
        name: 'APPLE',
        code: 'AAPL',
        category: 'STOCK',
        unit: 'USD/sh',
        color: '#38bdf8',
        borderColor: '#60a5fa',
        bgColor: 'rgba(56, 189, 248, 0.15)',
        decimals: 2
    },
    NVIDIA: {
        name: 'NVIDIA',
        code: 'NVDA',
        category: 'STOCK',
        unit: 'USD/sh',
        color: '#22c55e',
        borderColor: '#4ade80',
        bgColor: 'rgba(34, 197, 94, 0.15)',
        decimals: 2
    },
    TESLA: {
        name: 'TESLA',
        code: 'TSLA',
        category: 'STOCK',
        unit: 'USD/sh',
        color: '#ef4444',
        borderColor: '#f87171',
        bgColor: 'rgba(239, 68, 68, 0.15)',
        decimals: 2
    }
};

let activeCommodity = 'GOLD';
let stompClient = null;
let marketChart = null;
let latestPricesMap = {};
let activeCategoryFilter = 'ALL';

// Paper Trading State
let portfolioData = null;
let currentTradeSide = 'BUY';

// Initialize on DOM Ready
document.addEventListener('DOMContentLoaded', () => {
    initClock();
    initChart();
    initAlertForm();
    loadInitialPrices();
    loadInitialHistory(activeCommodity);
    loadTechnicalAnalysis(activeCommodity);
    fetchActiveAlerts();
    loadPortfolioData();
    initOrderExecution();
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

// ───────────────────────────────────────────────────────────
//  Chart Setup & Management
// ───────────────────────────────────────────────────────────
function initChart() {
    const canvas = document.getElementById('marketChart');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
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

    // Update active highlight on all ticker cards
    Object.keys(COMMODITY_CONFIG).forEach(k => {
        const card = document.getElementById(`card-${k}`);
        if (card) {
            if (k === activeCommodity) {
                card.classList.add('ring-2', 'ring-emerald-500/60', 'bg-slate-900/90');
            } else {
                card.classList.remove('ring-2', 'ring-emerald-500/60', 'bg-slate-900/90');
            }
        }
    });

    // Update Chart header
    const nameEl = document.getElementById('active-commodity-name');
    const codeEl = document.getElementById('active-commodity-code');
    const catEl = document.getElementById('active-commodity-category');
    const unitEl = document.getElementById('active-commodity-unit');
    const priceEl = document.getElementById('active-commodity-price');

    if (nameEl) nameEl.textContent = cfg.name;
    if (codeEl) codeEl.textContent = cfg.code;
    if (catEl) catEl.textContent = cfg.category;
    if (unitEl) unitEl.textContent = cfg.unit;

    if (priceEl && latestPricesMap[activeCommodity]) {
        priceEl.textContent = `$${formatNumber(latestPricesMap[activeCommodity].currentPrice, cfg.decimals)}`;
    }

    // Sync asset switcher dropdown
    const switcher = document.getElementById('chart-asset-switcher');
    if (switcher) switcher.value = activeCommodity;

    // Update Chart styles & dataset
    if (marketChart) {
        marketChart.data.datasets[0].label = `${cfg.name} (${cfg.code})`;
        marketChart.data.datasets[0].borderColor = cfg.borderColor;
        marketChart.data.datasets[0].backgroundColor = cfg.bgColor;
        marketChart.data.datasets[0].pointHoverBackgroundColor = cfg.borderColor;
    }

    // Load price history from backend
    loadInitialHistory(activeCommodity);

    // Load Technical Analysis (SMA, RSI, Signal)
    loadTechnicalAnalysis(activeCommodity);

    // Sync select dropdown in alert form
    const sel = document.getElementById('alert-commodity');
    if (sel) sel.value = activeCommodity;
    updateSuggestedPrice();

    // Sync order ticket in portfolio workbench
    onTradeAssetChange(activeCommodity);
}

// Category Filter (ALL, COMMODITY, CRYPTO, STOCK)
function filterCategory(cat) {
    activeCategoryFilter = cat;

    // Update header filter buttons
    ['ALL', 'COMMODITY', 'CRYPTO', 'STOCK'].forEach(c => {
        const btn = document.getElementById(`cat-btn-${c}`);
        if (btn) {
            if (c === cat) {
                btn.className = 'px-2.5 py-1 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 transition font-medium';
            } else {
                btn.className = 'px-2.5 py-1 rounded text-slate-400 hover:text-slate-200 transition';
            }
        }
    });

    // Update watchlist pill buttons
    const pills = document.querySelectorAll('.cat-pill');
    pills.forEach(pill => {
        if (pill.getAttribute('onclick').includes(cat)) {
            pill.className = 'cat-pill active px-3 py-1 rounded-md bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 transition';
        } else {
            pill.className = 'cat-pill px-3 py-1 rounded-md text-slate-400 hover:text-slate-200 transition';
        }
    });

    // Show/hide ticker cards based on data-category
    const cards = document.querySelectorAll('.ticker-card');
    cards.forEach(card => {
        const cardCat = card.getAttribute('data-category');
        if (cat === 'ALL' || cardCat === cat) {
            card.style.display = 'block';
        } else {
            card.style.display = 'none';
        }
    });
}

// ───────────────────────────────────────────────────────────
//  Technical Analysis Integration
// ───────────────────────────────────────────────────────────
function loadTechnicalAnalysis(commodity) {
    fetch(`/api/market/analysis/${commodity}`)
        .then(res => {
            if (!res.ok) throw new Error('Analysis unavailable');
            return res.json();
        })
        .then(ta => {
            const rsiEl = document.getElementById('active-rsi-value');
            const sma7El = document.getElementById('active-sma7-value');
            const signalBadge = document.getElementById('active-signal-badge');

            if (rsiEl) rsiEl.textContent = ta.rsi14 ? ta.rsi14.toFixed(1) : '--';
            if (sma7El) sma7El.textContent = ta.sma7 ? `$${formatNumber(ta.sma7, 2)}` : '--';

            if (signalBadge) {
                signalBadge.textContent = ta.signal || 'NEUTRAL';
                const color = ta.signalColor || '#94a3b8';
                signalBadge.style.color = color;
                signalBadge.style.borderColor = color + '40';
                signalBadge.style.backgroundColor = color + '15';
            }
        })
        .catch(() => {
            const signalBadge = document.getElementById('active-signal-badge');
            if (signalBadge) signalBadge.textContent = 'ACCUMULATING';
        });
}

// ───────────────────────────────────────────────────────────
//  WebSocket Connection (SockJS + STOMP)
// ───────────────────────────────────────────────────────────
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
            badgeEl.className = 'text-[11px] font-mono font-medium px-1.5 py-0.5 rounded bg-emerald-950/80 text-emerald-400 border border-emerald-800/60';
        } else if (pct < 0) {
            badgeEl.className = 'text-[11px] font-mono font-medium px-1.5 py-0.5 rounded bg-rose-950/80 text-rose-400 border border-rose-800/60';
        } else {
            badgeEl.className = 'text-[11px] font-mono font-medium px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 border border-slate-700';
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

        if (marketChart) {
            marketChart.data.labels.push(timeLabel);
            marketChart.data.datasets[0].data.push(price.currentPrice);

            // Keep rolling 50 data points on chart
            while (marketChart.data.labels.length > 50) {
                marketChart.data.labels.shift();
                marketChart.data.datasets[0].data.shift();
            }

            marketChart.update('none'); // smooth 60fps update
        }
    }

    // Update order execution market price if currently selected in trade box
    const tradeAssetSelect = document.getElementById('trade-asset-select');
    if (tradeAssetSelect && tradeAssetSelect.value === commodity) {
        const tradeMktPrice = document.getElementById('trade-market-price');
        if (tradeMktPrice) {
            tradeMktPrice.textContent = `$${formatNumber(price.currentPrice, cfg.decimals)}`;
        }
        calculateOrderEstimate();
    }

    // Update suggested price if alert form is pointing to this commodity
    const formCommodity = document.getElementById('alert-commodity');
    if (formCommodity && formCommodity.value === commodity) {
        updateSuggestedPrice();
    }

    // Periodically update live marked-to-market positions if holdings exist
    if (portfolioData && portfolioData.holdings && portfolioData.holdings.length > 0) {
        renderLiveHoldingsUpdate();
    }
}

// ───────────────────────────────────────────────────────────
//  Alert Handling & Terminal Notification Log
// ───────────────────────────────────────────────────────────
function handleAlertNotification(alert) {
    playAlertSound();
    showToastAlert(alert);
    appendAlertLog(alert);
    fetchActiveAlerts();
}

function showToastAlert(alert) {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = 'pointer-events-auto bg-slate-900 border-2 border-rose-500/80 text-white p-4 rounded-xl shadow-2xl shadow-rose-950/60 flex items-start gap-3 transition-all duration-300 transform translate-x-full';

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
    setTimeout(() => toast.classList.remove('translate-x-full'), 10);
    setTimeout(() => {
        toast.classList.add('translate-x-full', 'opacity-0');
        setTimeout(() => toast.remove(), 300);
    }, 7000);
}

function appendAlertLog(alert) {
    const container = document.getElementById('alert-logs-container');
    const placeholder = document.getElementById('empty-logs-placeholder');
    if (placeholder) placeholder.style.display = 'none';

    const logRow = document.createElement('div');
    logRow.className = 'flex flex-wrap items-center justify-between gap-2 p-2.5 rounded-lg bg-slate-900/90 border border-rose-900/40 hover:border-rose-700/60 transition';

    const time = formatTimestamp(alert.timestamp);

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

// ───────────────────────────────────────────────────────────
//  REST: Market Data & History
// ───────────────────────────────────────────────────────────
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
            onTradeAssetChange(activeCommodity);
        })
        .catch(err => console.error('Failed to load initial prices:', err));
}

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

// ───────────────────────────────────────────────────────────
//  Paper Trading & Portfolio Simulator Management
// ───────────────────────────────────────────────────────────
function loadPortfolioData() {
    fetch('/api/portfolio')
        .then(res => res.json())
        .then(data => {
            portfolioData = data;
            renderPortfolioKPIs();
            renderHoldingsTable();
        })
        .catch(err => console.error('Failed to fetch portfolio summary:', err));
}

function renderPortfolioKPIs() {
    if (!portfolioData) return;

    const netWorth = portfolioData.totalNetWorth !== undefined ? portfolioData.totalNetWorth : (portfolioData.totalPortfolioValue || 100000);
    const cash = portfolioData.cashBalance !== undefined ? portfolioData.cashBalance : 100000;
    const holdingsVal = portfolioData.totalHoldingValue !== undefined ? portfolioData.totalHoldingValue : (portfolioData.holdingsValue || 0);

    const netWorthEl = document.getElementById('portfolio-net-worth');
    const cashEl = document.getElementById('portfolio-cash');
    const holdingsValEl = document.getElementById('portfolio-holdings-val');
    const pnlEl = document.getElementById('portfolio-pnl');

    if (netWorthEl) netWorthEl.textContent = `$${formatNumber(netWorth, 2)}`;
    if (cashEl) cashEl.textContent = `$${formatNumber(cash, 2)}`;
    if (holdingsValEl) holdingsValEl.textContent = `$${formatNumber(holdingsVal, 2)}`;

    if (pnlEl) {
        const pnl = portfolioData.totalUnrealizedPnl !== undefined ? portfolioData.totalUnrealizedPnl : (portfolioData.totalProfitLoss || 0);
        const pnlPct = portfolioData.totalUnrealizedPnlPercent !== undefined ? portfolioData.totalUnrealizedPnlPercent : (portfolioData.totalProfitLossPercent || 0);
        const sign = pnl >= 0 ? '+' : '';
        pnlEl.textContent = `${sign}$${formatNumber(pnl, 2)} (${sign}${pnlPct.toFixed(2)}%)`;

        if (pnl > 0) {
            pnlEl.className = 'text-2xl font-bold font-mono-numbers text-emerald-400 mt-1';
        } else if (pnl < 0) {
            pnlEl.className = 'text-2xl font-bold font-mono-numbers text-rose-400 mt-1';
        } else {
            pnlEl.className = 'text-2xl font-bold font-mono-numbers text-slate-400 mt-1';
        }
    }
}

function renderHoldingsTable() {
    const tbody = document.getElementById('holdings-tbody');
    const badge = document.getElementById('holdings-count-badge');
    if (!tbody || !portfolioData) return;

    const holdings = portfolioData.holdings || [];
    if (badge) badge.textContent = holdings.length;

    if (holdings.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="7" class="py-8 text-center text-slate-500 font-mono text-xs">
                    No active positions in simulated account. Execute a buy order to build your portfolio.
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = holdings.map(h => {
        const pnl = h.unrealizedPnl !== undefined ? h.unrealizedPnl : (h.unrealizedProfitLoss || 0);
        const pnlPct = h.unrealizedPnlPercent !== undefined ? h.unrealizedPnlPercent : (h.unrealizedProfitLossPercent || 0);
        const avgPrice = h.avgBuyPrice !== undefined ? h.avgBuyPrice : (h.averageCostBasis || 0);
        const sign = pnl >= 0 ? '+' : '';
        const pnlColor = pnl >= 0 ? 'text-emerald-400' : 'text-rose-400';
        const cfg = COMMODITY_CONFIG[h.commodity] || { code: h.commodity, name: h.commodity };

        return `
            <tr class="hover:bg-slate-800/40 transition">
                <td class="py-2.5 font-bold text-white flex items-center gap-1.5">
                    <span>${cfg.name}</span>
                    <span class="text-[10px] text-slate-400 font-normal">(${cfg.code})</span>
                </td>
                <td class="py-2.5 text-right text-slate-200">${h.quantity.toFixed(4)}</td>
                <td class="py-2.5 text-right text-slate-400">$${formatNumber(avgPrice, 2)}</td>
                <td class="py-2.5 text-right text-white font-bold" id="live-holdings-mkt-${h.commodity}">$${formatNumber(h.currentPrice, 2)}</td>
                <td class="py-2.5 text-right text-slate-200 font-bold" id="live-holdings-val-${h.commodity}">$${formatNumber(h.currentValue, 2)}</td>
                <td class="py-2.5 text-right font-bold ${pnlColor}" id="live-holdings-pnl-${h.commodity}">
                    ${sign}$${formatNumber(pnl, 2)} (${sign}${pnlPct.toFixed(2)}%)
                </td>
                <td class="py-2.5 text-center">
                    <button onclick="quickSellPosition('${h.commodity}', ${h.quantity})" class="px-2 py-0.5 rounded text-[10px] font-bold bg-rose-950/80 text-rose-300 border border-rose-800/80 hover:bg-rose-900 transition">
                        CLOSE
                    </button>
                </td>
            </tr>
        `;
    }).join('');
}

// Live marked-to-market calculations on ticker updates
function renderLiveHoldingsUpdate() {
    if (!portfolioData || !portfolioData.holdings) return;
    let totalHoldingsVal = 0;
    let totalCost = 0;

    portfolioData.holdings.forEach(h => {
        const livePrice = latestPricesMap[h.commodity] ? latestPricesMap[h.commodity].currentPrice : h.currentPrice;
        const currentVal = h.quantity * livePrice;
        const costBasis = h.avgBuyPrice !== undefined ? h.avgBuyPrice : (h.averageCostBasis || 0);
        const cost = h.quantity * costBasis;
        const pnl = currentVal - cost;
        const pnlPct = cost > 0 ? (pnl / cost) * 100 : 0;

        totalHoldingsVal += currentVal;
        totalCost += cost;

        const mktEl = document.getElementById(`live-holdings-mkt-${h.commodity}`);
        const valEl = document.getElementById(`live-holdings-val-${h.commodity}`);
        const pnlEl = document.getElementById(`live-holdings-pnl-${h.commodity}`);

        if (mktEl) mktEl.textContent = `$${formatNumber(livePrice, 2)}`;
        if (valEl) valEl.textContent = `$${formatNumber(currentVal, 2)}`;
        if (pnlEl) {
            const sign = pnl >= 0 ? '+' : '';
            pnlEl.textContent = `${sign}$${formatNumber(pnl, 2)} (${sign}${pnlPct.toFixed(2)}%)`;
            pnlEl.className = `py-2.5 text-right font-bold ${pnl >= 0 ? 'text-emerald-400' : 'text-rose-400'}`;
        }
    });

    const cash = portfolioData.cashBalance || 0;
    const netWorth = cash + totalHoldingsVal;
    const totalPnl = (netWorth - 100000.0);
    const totalPnlPct = (totalPnl / 100000.0) * 100;

    const netWorthEl = document.getElementById('portfolio-net-worth');
    const holdingsValEl = document.getElementById('portfolio-holdings-val');
    const pnlEl = document.getElementById('portfolio-pnl');

    if (netWorthEl) netWorthEl.textContent = `$${formatNumber(netWorth, 2)}`;
    if (holdingsValEl) holdingsValEl.textContent = `$${formatNumber(totalHoldingsVal, 2)}`;
    if (pnlEl) {
        const sign = totalPnl >= 0 ? '+' : '';
        pnlEl.textContent = `${sign}$${formatNumber(totalPnl, 2)} (${sign}${totalPnlPct.toFixed(2)}%)`;
        pnlEl.className = `text-2xl font-bold font-mono-numbers mt-1 ${totalPnl >= 0 ? 'text-emerald-400' : 'text-rose-400'}`;
    }
}

function loadTransactionsData() {
    fetch('/api/portfolio/transactions')
        .then(res => res.json())
        .then(txs => {
            const tbody = document.getElementById('transactions-tbody');
            if (!tbody) return;

            if (!Array.isArray(txs) || txs.length === 0) {
                tbody.innerHTML = `
                    <tr>
                        <td colspan="6" class="py-8 text-center text-slate-500 font-mono text-xs">
                            No transactions recorded in SQLite ledger yet.
                        </td>
                    </tr>
                `;
                return;
            }

            tbody.innerHTML = txs.map(t => {
                const isBuy = t.action === 'BUY' || t.type === 'BUY';
                const time = t.createdAt || t.timestamp;
                const typeBadge = isBuy
                    ? `<span class="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">BUY</span>`
                    : `<span class="px-2 py-0.5 rounded text-[10px] font-bold bg-rose-500/20 text-rose-400 border border-rose-500/30">SELL</span>`;

                return `
                    <tr class="hover:bg-slate-800/40 transition">
                        <td class="py-2.5 text-slate-400">${formatTimestamp(time)}</td>
                        <td class="py-2.5">${typeBadge}</td>
                        <td class="py-2.5 font-bold text-white">${t.commodity}</td>
                        <td class="py-2.5 text-right text-slate-200">${Number(t.quantity).toFixed(4)}</td>
                        <td class="py-2.5 text-right text-slate-400">$${formatNumber(t.price, 2)}</td>
                        <td class="py-2.5 text-right font-bold text-white">$${formatNumber(t.totalAmount, 2)}</td>
                    </tr>
                `;
            }).join('');
        })
        .catch(err => console.error('Failed to load transaction history:', err));
}

function switchPortfolioTab(tab) {
    const holdingsBtn = document.getElementById('tab-holdings-btn');
    const txBtn = document.getElementById('tab-transactions-btn');
    const holdingsView = document.getElementById('portfolio-holdings-view');
    const txView = document.getElementById('portfolio-transactions-view');

    if (tab === 'holdings') {
        holdingsBtn.className = 'px-3 py-1.5 rounded-lg bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 font-bold';
        txBtn.className = 'px-3 py-1.5 rounded-lg text-slate-400 hover:text-slate-200';
        holdingsView.classList.remove('hidden');
        txView.classList.add('hidden');
        loadPortfolioData();
    } else {
        txBtn.className = 'px-3 py-1.5 rounded-lg bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 font-bold';
        holdingsBtn.className = 'px-3 py-1.5 rounded-lg text-slate-400 hover:text-slate-200';
        txView.classList.remove('hidden');
        holdingsView.classList.add('hidden');
        loadTransactionsData();
    }
}

// ───────────────────────────────────────────────────────────
//  Order Execution Ticket
// ───────────────────────────────────────────────────────────
function initOrderExecution() {
    onTradeAssetChange(activeCommodity);
}

function setTradeSide(side) {
    currentTradeSide = side;
    const buyBtn = document.getElementById('btn-side-buy');
    const sellBtn = document.getElementById('btn-side-sell');
    const execBtn = document.getElementById('btn-execute-order');

    if (side === 'BUY') {
        buyBtn.className = 'py-2 text-xs font-mono font-bold rounded-lg bg-emerald-600 text-white shadow-lg shadow-emerald-950/40 border border-emerald-500/50 transition';
        sellBtn.className = 'py-2 text-xs font-mono font-bold rounded-lg bg-slate-800 text-slate-400 border border-slate-700 hover:text-slate-200 transition';
        if (execBtn) {
            execBtn.className = 'w-full py-2.5 px-4 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white font-semibold text-xs font-mono flex items-center justify-center gap-2 shadow-lg shadow-emerald-950/40 transition';
            execBtn.innerHTML = `<span>⚡</span> EXECUTE PAPER BUY`;
        }
    } else {
        sellBtn.className = 'py-2 text-xs font-mono font-bold rounded-lg bg-rose-600 text-white shadow-lg shadow-rose-950/40 border border-rose-500/50 transition';
        buyBtn.className = 'py-2 text-xs font-mono font-bold rounded-lg bg-slate-800 text-slate-400 border border-slate-700 hover:text-slate-200 transition';
        if (execBtn) {
            execBtn.className = 'w-full py-2.5 px-4 rounded-lg bg-rose-600 hover:bg-rose-500 text-white font-semibold text-xs font-mono flex items-center justify-center gap-2 shadow-lg shadow-rose-950/40 transition';
            execBtn.innerHTML = `<span>⚡</span> EXECUTE PAPER SELL`;
        }
    }

    updateMaxAvailableUnits();
    calculateOrderEstimate();
}

function onTradeAssetChange(commodity) {
    const sel = document.getElementById('trade-asset-select');
    if (sel && sel.value !== commodity) sel.value = commodity;

    const cfg = COMMODITY_CONFIG[commodity];
    if (!cfg) return;

    const pill = document.getElementById('trade-order-asset-pill');
    if (pill) pill.textContent = `${cfg.name} (${cfg.code})`;

    const mktPriceEl = document.getElementById('trade-market-price');
    const price = latestPricesMap[commodity] ? latestPricesMap[commodity].currentPrice : 100;
    if (mktPriceEl) mktPriceEl.textContent = `$${formatNumber(price, cfg.decimals)}`;

    updateMaxAvailableUnits();
    calculateOrderEstimate();
}

function updateMaxAvailableUnits() {
    const assetSelect = document.getElementById('trade-asset-select');
    const maxEl = document.getElementById('trade-max-units');
    if (!assetSelect || !maxEl) return;

    const commodity = assetSelect.value;
    const currentPrice = latestPricesMap[commodity] ? latestPricesMap[commodity].currentPrice : 100;

    if (currentTradeSide === 'BUY') {
        const cash = portfolioData ? portfolioData.cashBalance : 100000;
        const maxUnits = currentPrice > 0 ? (cash / currentPrice) : 0;
        maxEl.textContent = `Max Buy: ${maxUnits.toFixed(4)} units`;
    } else {
        let ownedUnits = 0;
        if (portfolioData && portfolioData.holdings) {
            const holding = portfolioData.holdings.find(h => h.commodity === commodity);
            if (holding) ownedUnits = holding.quantity;
        }
        maxEl.textContent = `Owned: ${ownedUnits.toFixed(4)} units`;
    }
}

function quickFillUnits(n) {
    const qtyInput = document.getElementById('trade-quantity');
    if (qtyInput) {
        qtyInput.value = n;
        calculateOrderEstimate();
    }
}

function quickFillPercent(pct) {
    const assetSelect = document.getElementById('trade-asset-select');
    const qtyInput = document.getElementById('trade-quantity');
    if (!assetSelect || !qtyInput) return;

    const commodity = assetSelect.value;
    const currentPrice = latestPricesMap[commodity] ? latestPricesMap[commodity].currentPrice : 100;

    if (currentTradeSide === 'BUY') {
        const cash = portfolioData ? portfolioData.cashBalance : 100000;
        const targetDollars = cash * pct;
        const units = currentPrice > 0 ? (targetDollars / currentPrice) : 0;
        qtyInput.value = units.toFixed(4);
    } else {
        let ownedUnits = 0;
        if (portfolioData && portfolioData.holdings) {
            const holding = portfolioData.holdings.find(h => h.commodity === commodity);
            if (holding) ownedUnits = holding.quantity;
        }
        qtyInput.value = (ownedUnits * pct).toFixed(4);
    }

    calculateOrderEstimate();
}

function calculateOrderEstimate() {
    const assetSelect = document.getElementById('trade-asset-select');
    const qtyInput = document.getElementById('trade-quantity');
    const totalEl = document.getElementById('trade-estimated-total');

    if (!assetSelect || !qtyInput || !totalEl) return;

    const commodity = assetSelect.value;
    const currentPrice = latestPricesMap[commodity] ? latestPricesMap[commodity].currentPrice : 0;
    const qty = parseFloat(qtyInput.value) || 0;
    const total = qty * currentPrice;

    totalEl.textContent = `$${formatNumber(total, 2)}`;
}

function submitPaperOrder() {
    const assetSelect = document.getElementById('trade-asset-select');
    const qtyInput = document.getElementById('trade-quantity');
    const btn = document.getElementById('btn-execute-order');

    if (!assetSelect || !qtyInput) return;

    const commodity = assetSelect.value;
    const quantity = parseFloat(qtyInput.value);

    if (isNaN(quantity) || quantity <= 0) {
        alert('Please enter a valid quantity greater than zero.');
        return;
    }

    const endpoint = currentTradeSide === 'BUY' ? '/api/portfolio/buy' : '/api/portfolio/sell';
    const originalText = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = `<span class="animate-spin">⌛</span> Executing Order...`;

    fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ commodity: commodity, quantity: quantity })
    })
    .then(async res => {
        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.error || 'Failed to execute trade');
        }
        return res.json();
    })
    .then(summary => {
        portfolioData = summary;
        renderPortfolioKPIs();
        renderHoldingsTable();
        loadTransactionsData();
        qtyInput.value = '';
        calculateOrderEstimate();
        updateMaxAvailableUnits();

        btn.innerHTML = `<span class="text-emerald-300">✓ Order Filled!</span>`;
        setTimeout(() => {
            btn.disabled = false;
            btn.innerHTML = originalText;
        }, 1200);
    })
    .catch(err => {
        console.error(err);
        alert(err.message);
        btn.disabled = false;
        btn.innerHTML = originalText;
    });
}

function tradeActiveAsset() {
    onTradeAssetChange(activeCommodity);
    const sec = document.getElementById('portfolio-section');
    if (sec) sec.scrollIntoView({ behavior: 'smooth' });
}

function quickSellPosition(commodity, qty) {
    onTradeAssetChange(commodity);
    setTradeSide('SELL');
    const qtyInput = document.getElementById('trade-quantity');
    if (qtyInput) qtyInput.value = qty;
    calculateOrderEstimate();
    const sec = document.getElementById('portfolio-section');
    if (sec) sec.scrollIntoView({ behavior: 'smooth' });
}

// ───────────────────────────────────────────────────────────
//  Threshold Alerts Form & Active Rules
// ───────────────────────────────────────────────────────────
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

// ───────────────────────────────────────────────────────────
//  Helpers & Formatters
// ───────────────────────────────────────────────────────────
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
//  Desktop App Controls, Sound & Shortcuts
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
        // AudioContext initialization postponed
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
    // Keyboard Hotkeys: 1-0 for commodities
    const keysMap = {
        '1': 'GOLD',
        '2': 'SILVER',
        '3': 'COFFEE',
        '4': 'CRUDE_OIL',
        '5': 'BITCOIN',
        '6': 'ETHEREUM',
        '7': 'SOLANA',
        '8': 'APPLE',
        '9': 'NVIDIA',
        '0': 'TESLA'
    };

    window.addEventListener('keydown', (e) => {
        if (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT' || e.target.tagName === 'TEXTAREA') {
            return;
        }

        if (keysMap[e.key]) {
            selectCommodity(keysMap[e.key]);
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
