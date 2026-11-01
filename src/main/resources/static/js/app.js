/**
 * SpendWise — Main Frontend Application Logic
 */

class SpendWiseApp {
    constructor() {
        this.currentView = 'dashboard';
        this.currentGroupId = null;
        this.groupMembers = [];
        this.chartCategoryDonut = null;
        this.chartMonthlyBar = null;
        this.currentTxType = 'EXPENSE';
        this.currentSplitMethod = 'EQUAL';
        this.debounceTimer = null;

        this.categories = {
            EXPENSE: ['Food', 'Transport', 'Shopping', 'Education', 'Entertainment', 'Bills', 'Health', 'Other'],
            INCOME: ['Salary', 'Allowance', 'Freelance', 'Scholarship', 'Other']
        };

        // Palette for Charts
        this.chartColors = [
            '#241817', // Espresso Brown (Leading anchor)
            '#4D0E13', // Burgundy
            '#C8A49F', // Dusty Pink
            '#D8C4AC', // Sand / Champagne
            '#63141A', // Wine Burgundy
            '#A9807B', // Deep Rose
            '#8C2128', // Crimson Accent
            '#3E7355'  // Muted Sage Accent
        ];
    }

    async init() {
        this.setTheme(document.documentElement.dataset.theme || 'light', false);
        const user = await window.api.initGuest();
        this.updateProfileUI(user.displayName);

        // Bind hash change listener
        window.addEventListener('hashchange', () => this.handleHashChange());

        // Parse initial hash or invite parameter
        this.handleHashChange();
    }

    setTheme(theme, persist = true) {
        const isDark = theme === 'dark';
        document.documentElement.dataset.theme = isDark ? 'dark' : 'light';

        const toggle = document.getElementById('themeToggle');
        if (toggle) {
            const label = `Switch to ${isDark ? 'light' : 'dark'} theme`;
            toggle.setAttribute('aria-label', label);
            toggle.setAttribute('title', label);
        }

        if (persist) {
            localStorage.setItem('spendwise_theme', isDark ? 'dark' : 'light');
        }

        const styles = getComputedStyle(document.documentElement);
        this.chartColors = Array.from({ length: 8 }, (_, index) =>
            styles.getPropertyValue(`--chart-color-${index + 1}`).trim()
        );

        if (this.dashboardData) {
            this.renderCategoryChart(this.dashboardData.categoryExpenses || {});
            this.renderMonthlyBarChart(this.dashboardData.monthlyOverview || []);
        }
    }

    toggleTheme() {
        const currentTheme = document.documentElement.dataset.theme || 'light';
        this.setTheme(currentTheme === 'dark' ? 'light' : 'dark');
    }

    updateProfileUI(name) {
        const initial = (name && name.length > 0) ? name.charAt(0).toUpperCase() : 'G';
        const avatarEl = document.getElementById('sidebarAvatar');
        const nameEl = document.getElementById('sidebarUserName');
        const settingsNameEl = document.getElementById('settingsDisplayName');
        const settingsIdEl = document.getElementById('settingsGuestId');

        if (avatarEl) avatarEl.textContent = initial;
        if (nameEl) nameEl.textContent = name;
        if (settingsNameEl) settingsNameEl.value = name;
        if (settingsIdEl) settingsIdEl.value = window.api.guestId;
    }

    // ========================================================
    // Navigation & Routing
    // ========================================================
    navigate(view, param = null) {
        if (view === 'group-detail' && param) {
            window.location.hash = `#group?id=${param}`;
        } else {
            window.location.hash = `#${view}`;
        }
    }

    handleHashChange() {
        const hash = window.location.hash.replace('#', '') || 'dashboard';

        // Check for join code in hash: e.g. #join?code=SW-XYZ
        if (hash.startsWith('join')) {
            const params = new URLSearchParams(hash.split('?')[1] || '');
            const code = params.get('code');
            this.switchView('groups');
            if (code) {
                this.openJoinGroupModal(code);
            }
            return;
        }

        if (hash.startsWith('group?id=')) {
            const groupId = hash.split('id=')[1];
            if (groupId) {
                this.currentGroupId = parseInt(groupId, 10);
                this.switchView('group-detail');
                this.loadGroupDetail(this.currentGroupId);
                return;
            }
        }

        const view = hash.split('?')[0];
        this.switchView(view);

        if (view === 'dashboard') {
            this.loadDashboard();
        } else if (view === 'transactions') {
            this.loadTransactions();
        } else if (view === 'groups') {
            this.loadGroups();
        }
    }

    switchView(viewName) {
        const validViews = ['dashboard', 'transactions', 'groups', 'group-detail', 'settings'];
        if (!validViews.includes(viewName)) {
            viewName = 'dashboard';
        }
        this.currentView = viewName;

        document.querySelectorAll('.view-section').forEach(el => el.classList.remove('active'));
        const targetView = document.getElementById(`view-${viewName}`);
        if (targetView) targetView.classList.add('active');

        document.querySelectorAll('.nav-link').forEach(link => {
            if (link.getAttribute('data-view') === viewName) {
                link.classList.add('active');
            } else {
                link.classList.remove('active');
            }
        });

        // Close mobile sidebar if open
        const sidebar = document.getElementById('sidebar');
        if (sidebar) sidebar.classList.remove('mobile-open');

        window.scrollTo({ top: 0, behavior: 'smooth' });
    }

    toggleSidebar() {
        const sidebar = document.getElementById('sidebar');
        if (sidebar) sidebar.classList.toggle('mobile-open');
    }

    // ========================================================
    // Helpers & Formatters
    // ========================================================
    formatMoney(amount) {
        if (amount === null || amount === undefined) return '₹0.00';
        const num = parseFloat(amount);
        return '₹' + num.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    formatDate(dateStr) {
        if (!dateStr) return '';
        const d = new Date(dateStr);
        return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    }

    escapeHtml(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    showToast(message, type = 'success') {
        const container = document.getElementById('toastContainer');
        if (!container) return;

        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.innerHTML = `
            <span>${this.escapeHtml(message)}</span>
            <button style="background:none; border:none; cursor:pointer; color:inherit; font-size:1.1rem;" onclick="this.parentElement.remove()">&times;</button>
        `;

        container.appendChild(toast);
        setTimeout(() => {
            toast.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(100%)';
            setTimeout(() => toast.remove(), 300);
        }, 4000);
    }

    // ========================================================
    // Modal Helpers
    // ========================================================
    openModal(id) {
        const el = document.getElementById(id);
        if (el) el.classList.add('active');
    }

    closeModal(id) {
        const el = document.getElementById(id);
        if (el) el.classList.remove('active');
    }

    // ========================================================
    // 1. DASHBOARD
    // ========================================================
    async loadDashboard() {
        const period = document.getElementById('dashPeriodSelect')?.value || 'ALL';
        try {
            const data = await window.api.getDashboardSummary(period);
            this.dashboardData = data;

            // Metrics
            document.getElementById('dashIncome').textContent = this.formatMoney(data.totalIncome);
            document.getElementById('dashExpenses').textContent = this.formatMoney(data.totalExpenses);
            document.getElementById('dashBalance').textContent = this.formatMoney(data.netBalance);

            // Render Donut Chart
            this.renderCategoryChart(data.categoryExpenses || {});

            // Render Monthly Bar Chart
            this.renderMonthlyBarChart(data.monthlyOverview || []);

            // Render Recent Transactions
            this.renderDashboardRecentTransactions(data.recentTransactions || []);

            // Render Active Groups
            this.renderDashboardActiveGroups(data.activeGroups || []);

        } catch (err) {
            this.showToast('Failed to load dashboard: ' + err.message, 'error');
        }
    }

    renderCategoryChart(categoryData) {
        const canvas = document.getElementById('categoryDonutChart');
        const emptyEl = document.getElementById('categoryChartEmpty');
        if (!canvas) return;
        const themeStyles = getComputedStyle(document.documentElement);

        const labels = Object.keys(categoryData);
        const values = Object.values(categoryData).map(v => parseFloat(v));

        if (labels.length === 0 || values.every(v => v === 0)) {
            canvas.style.display = 'none';
            if (emptyEl) emptyEl.style.display = 'block';
            if (this.chartCategoryDonut) {
                this.chartCategoryDonut.destroy();
                this.chartCategoryDonut = null;
            }
            return;
        }

        canvas.style.display = 'block';
        if (emptyEl) emptyEl.style.display = 'none';

        if (this.chartCategoryDonut) {
            this.chartCategoryDonut.destroy();
        }

        this.chartCategoryDonut = new Chart(canvas, {
            type: 'doughnut',
            data: {
                labels: labels,
                datasets: [{
                    data: values,
                    backgroundColor: this.chartColors.slice(0, labels.length),
                    borderWidth: 2,
                    borderColor: themeStyles.getPropertyValue('--chart-surface').trim(),
                    hoverOffset: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: {
                            font: { family: 'Plus Jakarta Sans', size: 12 },
                            color: themeStyles.getPropertyValue('--chart-text').trim(),
                            boxWidth: 12,
                            padding: 12
                        }
                    },
                    tooltip: {
                        callbacks: {
                            label: (ctx) => ` ${ctx.label}: ${this.formatMoney(ctx.raw)}`
                        }
                    }
                },
                cutout: '68%'
            }
        });
    }

    renderMonthlyBarChart(monthlyPoints) {
        const canvas = document.getElementById('monthlyBarChart');
        if (!canvas) return;
        const themeStyles = getComputedStyle(document.documentElement);

        const labels = monthlyPoints.map(p => p.month);
        const incomeData = monthlyPoints.map(p => parseFloat(p.income || 0));
        const expenseData = monthlyPoints.map(p => parseFloat(p.expense || 0));

        if (this.chartMonthlyBar) {
            this.chartMonthlyBar.destroy();
        }

        this.chartMonthlyBar = new Chart(canvas, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [
                    {
                        label: 'Income',
                        data: incomeData,
                        backgroundColor: themeStyles.getPropertyValue('--chart-income').trim(),
                        borderRadius: 6
                    },
                    {
                        label: 'Expense',
                        data: expenseData,
                        backgroundColor: themeStyles.getPropertyValue('--chart-expense').trim(),
                        borderRadius: 6
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    x: {
                        grid: { display: false },
                        ticks: { font: { family: 'Plus Jakarta Sans', size: 11 }, color: themeStyles.getPropertyValue('--chart-text').trim() }
                    },
                    y: {
                        grid: { color: themeStyles.getPropertyValue('--chart-grid').trim() },
                        ticks: {
                            font: { family: 'Plus Jakarta Sans', size: 11 },
                            color: themeStyles.getPropertyValue('--chart-text').trim(),
                            callback: (val) => '₹' + val
                        }
                    }
                },
                plugins: {
                    legend: {
                        position: 'top',
                        labels: { font: { family: 'Plus Jakarta Sans', size: 12 }, color: themeStyles.getPropertyValue('--chart-text').trim() }
                    },
                    tooltip: {
                        callbacks: {
                            label: (ctx) => ` ${ctx.dataset.label}: ${this.formatMoney(ctx.raw)}`
                        }
                    }
                }
            }
        });
    }

    renderDashboardRecentTransactions(transactions) {
        const container = document.getElementById('dashRecentTransactionsList');
        if (!container) return;

        if (transactions.length === 0) {
            container.innerHTML = `
                <div class="empty-state" style="padding:24px; border:none;">
                    <p class="empty-state-text" style="margin-bottom:0;">No recent personal transactions.</p>
                </div>
            `;
            return;
        }

        let html = '<div style="display:flex; flex-direction:column; gap:10px;">';
        transactions.forEach(tx => {
            const isIncome = tx.transactionType === 'INCOME';
            const badgeClass = isIncome ? 'badge-income' : 'badge-expense';
            const sign = isIncome ? '+' : '-';
            const amountColor = isIncome ? 'var(--success)' : 'var(--text-primary)';

            html += `
                <div style="display:flex; align-items:center; justify-content:space-between; padding:10px 14px; background:var(--creme-light); border-radius:var(--radius-sm); border:1px solid var(--border-soft);">
                    <div style="display:flex; align-items:center; gap:12px;">
                        <span class="badge ${badgeClass}">${tx.category}</span>
                        <div>
                            <div style="font-size:0.9rem; font-weight:600; color:var(--text-primary);">${this.escapeHtml(tx.title)}</div>
                            <div style="font-size:0.75rem; color:var(--text-muted);">${this.formatDate(tx.transactionDate)}</div>
                        </div>
                    </div>
                    <div style="font-size:0.95rem; font-weight:700; color:${amountColor};">
                        ${sign}${this.formatMoney(tx.amount)}
                    </div>
                </div>
            `;
        });
        html += '</div>';
        container.innerHTML = html;
    }

    renderDashboardActiveGroups(groups) {
        const container = document.getElementById('dashActiveGroupsList');
        if (!container) return;

        if (groups.length === 0) {
            container.innerHTML = `
                <div class="empty-state" style="padding:24px; border:none;">
                    <p class="empty-state-text" style="margin-bottom:0;">No active shared groups yet.</p>
                </div>
            `;
            return;
        }

        let html = '<div style="display:flex; flex-direction:column; gap:10px;">';
        groups.forEach(g => {
            const bal = parseFloat(g.userBalance || 0);
            let balBadge = `<span class="badge badge-settled">Settled</span>`;
            if (bal > 0.005) {
                balBadge = `<span class="badge badge-receives">+${this.formatMoney(bal)} back</span>`;
            } else if (bal < -0.005) {
                balBadge = `<span class="badge badge-owes">Owes ${this.formatMoney(Math.abs(bal))}</span>`;
            }

            html += `
                <div style="display:flex; align-items:center; justify-content:space-between; padding:12px 14px; background:var(--creme-light); border-radius:var(--radius-sm); border:1px solid var(--border-soft); cursor:pointer;" onclick="app.navigate('group-detail', ${g.groupId})">
                    <div>
                        <div style="font-size:0.94rem; font-weight:700; color:var(--burgundy);">${this.escapeHtml(g.groupName)}</div>
                        <div style="font-size:0.75rem; color:var(--text-muted);">${g.memberCount} members • Code: ${this.escapeHtml(g.inviteCode)}</div>
                    </div>
                    <div>${balBadge}</div>
                </div>
            `;
        });
        html += '</div>';
        container.innerHTML = html;
    }

    // ========================================================
    // 2. PERSONAL TRANSACTIONS
    // ========================================================
    debounceLoadTransactions() {
        clearTimeout(this.debounceTimer);
        this.debounceTimer = setTimeout(() => this.loadTransactions(), 300);
    }

    resetTransactionFilters() {
        document.getElementById('txSearchInput').value = '';
        document.getElementById('txTypeFilter').value = 'ALL';
        document.getElementById('txCategoryFilter').value = 'ALL';
        document.getElementById('txSortBy').value = 'date_desc';
        this.loadTransactions();
    }

    async loadTransactions() {
        const search = document.getElementById('txSearchInput')?.value || '';
        const type = document.getElementById('txTypeFilter')?.value || 'ALL';
        const category = document.getElementById('txCategoryFilter')?.value || 'ALL';
        const sortVal = document.getElementById('txSortBy')?.value || 'date_desc';

        const [sortBy, sortDirection] = sortVal.split('_');

        try {
            const list = await window.api.getTransactions({
                search,
                type: type === 'ALL' ? null : type,
                category: category === 'ALL' ? null : category,
                sortBy,
                sortDirection
            });

            this.renderTransactionsTable(list);
        } catch (err) {
            this.showToast('Failed to load transactions: ' + err.message, 'error');
        }
    }

    renderTransactionsTable(transactions) {
        const tbody = document.getElementById('transactionsTableBody');
        const emptyState = document.getElementById('transactionsEmptyState');
        const tableContainer = document.querySelector('#view-transactions .data-table-container');

        if (!tbody) return;

        if (transactions.length === 0) {
            tbody.innerHTML = '';
            if (tableContainer) tableContainer.style.display = 'none';
            if (emptyState) emptyState.style.display = 'block';
            return;
        }

        if (tableContainer) tableContainer.style.display = 'block';
        if (emptyState) emptyState.style.display = 'none';

        tbody.innerHTML = transactions.map(tx => {
            const isIncome = tx.transactionType === 'INCOME';
            const badgeClass = isIncome ? 'badge-income' : 'badge-expense';
            const sign = isIncome ? '+' : '-';
            const amountColor = isIncome ? 'var(--success)' : 'var(--text-primary)';

            return `
                <tr>
                    <td style="white-space:nowrap; color:var(--text-muted); font-size:0.86rem;">
                        ${this.formatDate(tx.transactionDate)}
                    </td>
                    <td>
                        <div style="font-weight:600; color:var(--text-primary);">${this.escapeHtml(tx.title)}</div>
                        ${tx.description ? `<div style="font-size:0.78rem; color:var(--text-muted); margin-top:2px;">${this.escapeHtml(tx.description)}</div>` : ''}
                    </td>
                    <td>
                        <span class="badge badge-category">${this.escapeHtml(tx.category)}</span>
                    </td>
                    <td>
                        <span class="badge ${badgeClass}">${tx.transactionType}</span>
                    </td>
                    <td style="text-align:right; font-weight:700; color:${amountColor}; font-size:0.95rem;">
                        ${sign}${this.formatMoney(tx.amount)}
                    </td>
                    <td style="text-align:center; white-space:nowrap;">
                        <button class="btn btn-outline btn-sm" title="Edit" onclick="app.editTransaction(${tx.id})">
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                            </svg>
                        </button>
                        <button class="btn btn-danger btn-sm" title="Delete" onclick="app.confirmDeleteTransaction(${tx.id})">
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <polyline points="3 6 5 6 21 6"></polyline>
                                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                            </svg>
                        </button>
                    </td>
                </tr>
            `;
        }).join('');
    }

    openTransactionModal(editData = null) {
        const titleEl = document.getElementById('txModalTitle');
        const idEl = document.getElementById('txId');
        const titleInput = document.getElementById('txTitle');
        const amountInput = document.getElementById('txAmount');
        const dateInput = document.getElementById('txDate');
        const descInput = document.getElementById('txDescription');

        if (editData) {
            titleEl.textContent = 'Edit Transaction';
            idEl.value = editData.id;
            titleInput.value = editData.title;
            amountInput.value = editData.amount;
            dateInput.value = editData.transactionDate;
            descInput.value = editData.description || '';
            this.setTxType(editData.transactionType, editData.category);
        } else {
            titleEl.textContent = 'Add Transaction';
            idEl.value = '';
            titleInput.value = '';
            amountInput.value = '';
            dateInput.value = new Date().toISOString().split('T')[0];
            descInput.value = '';
            this.setTxType('EXPENSE');
        }

        this.openModal('modalTransaction');
    }

    setTxType(type, selectedCategory = null) {
        this.currentTxType = type;
        const expBtn = document.getElementById('txTypeExpenseBtn');
        const incBtn = document.getElementById('txTypeIncomeBtn');
        const catSelect = document.getElementById('txCategory');

        if (type === 'EXPENSE') {
            expBtn?.classList.add('active');
            incBtn?.classList.remove('active');
        } else {
            incBtn?.classList.add('active');
            expBtn?.classList.remove('active');
        }

        // Repopulate categories
        const list = this.categories[type] || ['Other'];
        catSelect.innerHTML = list.map(c => `<option value="${c}" ${c === selectedCategory ? 'selected' : ''}>${c}</option>`).join('');
    }

    async saveTransaction() {
        const id = document.getElementById('txId')?.value;
        const title = document.getElementById('txTitle')?.value?.trim();
        const amount = document.getElementById('txAmount')?.value;
        const date = document.getElementById('txDate')?.value;
        const category = document.getElementById('txCategory')?.value;
        const description = document.getElementById('txDescription')?.value?.trim();

        if (!title) {
            this.showToast('Please enter a transaction title', 'warning');
            return;
        }
        if (!amount || parseFloat(amount) <= 0) {
            this.showToast('Please enter a valid amount greater than 0', 'warning');
            return;
        }
        if (!date) {
            this.showToast('Please choose a transaction date', 'warning');
            return;
        }

        const payload = {
            title,
            amount: parseFloat(amount),
            transactionType: this.currentTxType,
            category,
            transactionDate: date,
            description
        };

        try {
            if (id) {
                await window.api.updateTransaction(id, payload);
                this.showToast('Transaction updated successfully');
            } else {
                await window.api.createTransaction(payload);
                this.showToast('Transaction added successfully');
            }
            this.closeModal('modalTransaction');
            this.loadTransactions();
            if (this.currentView === 'dashboard') {
                this.loadDashboard();
            }
        } catch (err) {
            this.showToast(err.message, 'error');
        }
    }

    async editTransaction(id) {
        try {
            const tx = await window.api.request(`/transactions/${id}`);
            this.openTransactionModal(tx);
        } catch (err) {
            this.showToast('Failed to fetch transaction details: ' + err.message, 'error');
        }
    }

    confirmDeleteTransaction(id) {
        const titleEl = document.getElementById('confirmModalTitle');
        const msgEl = document.getElementById('confirmModalMessage');
        const actionBtn = document.getElementById('confirmModalActionBtn');

        titleEl.textContent = 'Delete Transaction';
        msgEl.textContent = 'Are you sure you want to permanently delete this transaction?';
        actionBtn.onclick = async () => {
            try {
                await window.api.deleteTransaction(id);
                this.closeModal('modalConfirm');
                this.showToast('Transaction deleted');
                this.loadTransactions();
                if (this.currentView === 'dashboard') {
                    this.loadDashboard();
                }
            } catch (err) {
                this.showToast(err.message, 'error');
            }
        };

        this.openModal('modalConfirm');
    }

    // ========================================================
    // 3. SHARED GROUPS
    // ========================================================
    async loadGroups() {
        try {
            const groups = await window.api.getGroups();
            this.renderGroupsGrid(groups);
        } catch (err) {
            this.showToast('Failed to load groups: ' + err.message, 'error');
        }
    }

    renderGroupsGrid(groups) {
        const grid = document.getElementById('groupsGrid');
        const emptyState = document.getElementById('groupsEmptyState');
        if (!grid) return;

        if (groups.length === 0) {
            grid.innerHTML = '';
            if (emptyState) emptyState.style.display = 'block';
            return;
        }

        if (emptyState) emptyState.style.display = 'none';

        grid.innerHTML = groups.map(g => `
            <div class="group-card" onclick="app.navigate('group-detail', ${g.id})">
                <div>
                    <div class="group-card-header">
                        <h3 class="group-name">${this.escapeHtml(g.groupName)}</h3>
                        <span class="badge badge-category">${this.escapeHtml(g.inviteCode)}</span>
                    </div>
                    <p class="group-desc">${this.escapeHtml(g.description || 'No description provided.')}</p>
                </div>
                <div class="group-meta">
                    <span>${g.memberCount} ${g.memberCount === 1 ? 'member' : 'members'}</span>
                    <span style="font-weight:700; color:var(--burgundy);">Spent: ${this.formatMoney(g.totalSpent || 0)}</span>
                </div>
            </div>
        `).join('');
    }

    openCreateGroupModal() {
        document.getElementById('cgName').value = '';
        document.getElementById('cgDescription').value = '';
        this.openModal('modalCreateGroup');
    }

    async saveCreateGroup() {
        const groupName = document.getElementById('cgName')?.value?.trim();
        const description = document.getElementById('cgDescription')?.value?.trim();

        if (!groupName) {
            this.showToast('Please enter a group name', 'warning');
            return;
        }

        try {
            const group = await window.api.createGroup({ groupName, description });
            this.closeModal('modalCreateGroup');
            this.showToast(`Group "${group.groupName}" created!`);
            this.navigate('group-detail', group.id);
        } catch (err) {
            this.showToast(err.message, 'error');
        }
    }

    openJoinGroupModal(code = '') {
        const codeInput = document.getElementById('jgCode');
        const previewBox = document.getElementById('jgPreviewBox');
        if (codeInput) codeInput.value = code;
        if (previewBox) previewBox.style.display = 'none';

        if (code) {
            this.previewGroupCode();
        }
        this.openModal('modalJoinGroup');
    }

    async previewGroupCode() {
        const code = document.getElementById('jgCode')?.value?.trim();
        const previewBox = document.getElementById('jgPreviewBox');
        const titleEl = document.getElementById('jgPreviewTitle');
        const descEl = document.getElementById('jgPreviewDesc');
        const countEl = document.getElementById('jgPreviewCount');

        if (!code) {
            this.showToast('Please enter an invite code', 'warning');
            return;
        }

        try {
            const group = await window.api.previewGroup(code);
            titleEl.textContent = group.groupName;
            descEl.textContent = group.description || 'No description';
            countEl.textContent = `${group.memberCount} members`;
            previewBox.style.display = 'block';
        } catch (err) {
            this.showToast(err.message, 'error');
            if (previewBox) previewBox.style.display = 'none';
        }
    }

    async confirmJoinGroup() {
        const code = document.getElementById('jgCode')?.value?.trim();
        if (!code) {
            this.showToast('Please enter an invite code', 'warning');
            return;
        }

        try {
            const group = await window.api.joinGroup(code);
            this.closeModal('modalJoinGroup');
            this.showToast(`Joined group "${group.groupName}" successfully!`);
            this.navigate('group-detail', group.id);
        } catch (err) {
            this.showToast(err.message, 'error');
        }
    }

    // ========================================================
    // 4. GROUP DETAILS VIEW
    // ========================================================
    async loadGroupDetail(groupId) {
        try {
            const [group, members, balancesData, expenses] = await Promise.all([
                window.api.getGroup(groupId),
                window.api.getGroupMembers(groupId),
                window.api.getGroupBalances(groupId),
                window.api.getGroupExpenses(groupId)
            ]);

            this.currentGroup = group;
            this.groupMembers = members;

            // Render Banner Info
            document.getElementById('gdGroupName').textContent = group.groupName;
            document.getElementById('gdGroupDesc').textContent = group.description || 'Shared group expenses & balance settlement';
            document.getElementById('gdInviteCode').textContent = group.inviteCode;

            // Total spent in group
            const totalSpent = expenses.reduce((sum, e) => sum + parseFloat(e.totalAmount || 0), 0);
            document.getElementById('gdTotalSpentBadge').textContent = `Total Spent: ${this.formatMoney(totalSpent)}`;

            // Render Balances
            this.renderGroupBalances(balancesData.balances || []);

            // Render Settlement Suggestions
            this.renderGroupSettlements(balancesData.settlements || []);

            // Render Expenses List
            this.renderGroupExpenses(expenses || []);

        } catch (err) {
            this.showToast('Failed to load group details: ' + err.message, 'error');
            this.navigate('groups');
        }
    }

    copyInviteCode() {
        if (!this.currentGroup) return;
        navigator.clipboard.writeText(this.currentGroup.inviteCode).then(() => {
            this.showToast(`Invite code ${this.currentGroup.inviteCode} copied to clipboard!`);
        }).catch(() => {
            this.showToast(`Code: ${this.currentGroup.inviteCode}`);
        });
    }

    copyInviteLink() {
        if (!this.currentGroup) return;
        const link = `${window.location.origin}/#join?code=${encodeURIComponent(this.currentGroup.inviteCode)}`;
        navigator.clipboard.writeText(link).then(() => {
            this.showToast('Shareable invitation link copied to clipboard!');
        }).catch(() => {
            this.showToast(`Link: ${link}`);
        });
    }

    renderGroupBalances(balances) {
        const container = document.getElementById('gdBalancesList');
        if (!container) return;

        if (balances.length === 0) {
            container.innerHTML = `<p class="empty-state-text">No member records.</p>`;
            return;
        }

        let html = '<div style="display:flex; flex-direction:column; gap:10px;">';
        balances.forEach(b => {
            const bal = parseFloat(b.netBalance || 0);
            let badgeHtml = '';
            if (b.status === 'RECEIVES') {
                badgeHtml = `<span class="badge badge-receives">+${this.formatMoney(bal)} back</span>`;
            } else if (b.status === 'OWES') {
                badgeHtml = `<span class="badge badge-owes">Owes ${this.formatMoney(Math.abs(bal))}</span>`;
            } else {
                badgeHtml = `<span class="badge badge-settled">Settled</span>`;
            }

            html += `
                <div style="display:flex; align-items:center; justify-content:space-between; padding:12px 14px; background:var(--creme-light); border-radius:var(--radius-sm); border:1px solid var(--border-soft);">
                    <div style="display:flex; align-items:center; gap:10px;">
                        <div class="avatar" style="width:32px; height:32px; font-size:0.8rem;">${this.escapeHtml(b.displayName.charAt(0))}</div>
                        <div>
                            <div style="font-weight:700; color:var(--text-primary); font-size:0.9rem;">${this.escapeHtml(b.displayName)}</div>
                            <div style="font-size:0.75rem; color:var(--text-muted);">Paid: ${this.formatMoney(b.totalPaid)} • Share: ${this.formatMoney(b.totalOwed)}</div>
                        </div>
                    </div>
                    <div>${badgeHtml}</div>
                </div>
            `;
        });
        html += '</div>';
        container.innerHTML = html;
    }

    renderGroupSettlements(settlements) {
        const container = document.getElementById('gdSettlementsList');
        if (!container) return;

        if (settlements.length === 0) {
            container.innerHTML = `
                <div style="text-align:center; padding:20px; color:var(--text-muted); font-size:0.88rem;">
                    🎉 All balances are completely settled! No payments needed.
                </div>
            `;
            return;
        }

        let html = '<div style="display:flex; flex-direction:column; gap:8px;">';
        settlements.forEach(s => {
            html += `
                <div class="settlement-item">
                    <div class="settlement-flow">
                        <span style="color:var(--burgundy); font-weight:700;">${this.escapeHtml(s.fromMemberName)}</span>
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="color:var(--dusty-pink-dark);">
                            <line x1="5" y1="12" x2="19" y2="12"></line>
                            <polyline points="12 5 19 12 12 19"></polyline>
                        </svg>
                        <span style="color:var(--burgundy); font-weight:700;">${this.escapeHtml(s.toMemberName)}</span>
                    </div>
                    <span style="font-size:0.95rem; font-weight:700; color:var(--burgundy);">${this.formatMoney(s.amount)}</span>
                </div>
            `;
        });
        html += '</div>';
        container.innerHTML = html;
    }

    renderGroupExpenses(expenses) {
        const tbody = document.getElementById('gdExpensesTableBody');
        const emptyState = document.getElementById('gdExpensesEmptyState');
        const tableContainer = document.querySelector('#view-group-detail .data-table-container');

        if (!tbody) return;

        if (expenses.length === 0) {
            tbody.innerHTML = '';
            if (tableContainer) tableContainer.style.display = 'none';
            if (emptyState) emptyState.style.display = 'block';
            return;
        }

        if (tableContainer) tableContainer.style.display = 'block';
        if (emptyState) emptyState.style.display = 'none';

        tbody.innerHTML = expenses.map(exp => {
            const splitBadgeClass = exp.splitType === 'EQUAL' ? 'badge-category' : 'badge-category';
            const splitsSummary = (exp.splits || []).map(s => `
                <span class="badge" style="background:#FFF; border:1px solid var(--sand); font-size:0.72rem; margin:2px;">
                    ${this.escapeHtml(s.memberName)}: ${this.formatMoney(s.shareAmount)}
                </span>
            `).join('');

            return `
                <tr>
                    <td style="white-space:nowrap; color:var(--text-muted); font-size:0.86rem;">
                        ${this.formatDate(exp.expenseDate)}
                    </td>
                    <td>
                        <div style="font-weight:700; color:var(--text-primary); font-size:0.92rem;">${this.escapeHtml(exp.description)}</div>
                        ${exp.notes ? `<div style="font-size:0.78rem; color:var(--text-muted); margin-top:2px;">${this.escapeHtml(exp.notes)}</div>` : ''}
                    </td>
                    <td>
                        <div style="font-weight:600; color:var(--burgundy); font-size:0.88rem;">${this.escapeHtml(exp.paidByName || 'Member')}</div>
                    </td>
                    <td>
                        <span class="badge ${splitBadgeClass}">${exp.splitType}</span>
                    </td>
                    <td style="text-align:right; font-weight:700; font-size:0.95rem; color:var(--burgundy);">
                        ${this.formatMoney(exp.totalAmount)}
                    </td>
                    <td style="text-align:center;">
                        <div style="display:flex; flex-wrap:wrap; justify-content:center; max-width:240px; margin:0 auto;">
                            ${splitsSummary}
                        </div>
                    </td>
                    <td style="text-align:center; white-space:nowrap;">
                        <button class="btn btn-outline btn-sm" title="Edit" onclick="app.editExpense(${exp.id})">
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                            </svg>
                        </button>
                        <button class="btn btn-danger btn-sm" title="Delete" onclick="app.confirmDeleteExpense(${exp.id})">
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <polyline points="3 6 5 6 21 6"></polyline>
                                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                            </svg>
                        </button>
                    </td>
                </tr>
            `;
        }).join('');
    }

    openExpenseModal(editData = null) {
        if (!this.currentGroupId) return;

        const titleEl = document.getElementById('expModalTitle');
        const idEl = document.getElementById('expId');
        const descInput = document.getElementById('expDescription');
        const amountInput = document.getElementById('expAmount');
        const paidBySelect = document.getElementById('expPaidBy');
        const dateInput = document.getElementById('expDate');
        const notesInput = document.getElementById('expNotes');

        // Populate Payer dropdown with members
        paidBySelect.innerHTML = this.groupMembers.map(m => `
            <option value="${m.id}">${this.escapeHtml(m.displayName)}</option>
        `).join('');

        if (editData) {
            titleEl.textContent = 'Edit Shared Expense';
            idEl.value = editData.id;
            descInput.value = editData.description;
            amountInput.value = editData.totalAmount;
            paidBySelect.value = editData.paidBy;
            dateInput.value = editData.expenseDate;
            notesInput.value = editData.notes || '';
            this.setSplitMethod(editData.splitType || 'EQUAL', editData.splits);
        } else {
            titleEl.textContent = 'Add Shared Expense';
            idEl.value = '';
            descInput.value = '';
            amountInput.value = '';
            dateInput.value = new Date().toISOString().split('T')[0];
            notesInput.value = '';
            this.setSplitMethod('EQUAL');
        }

        this.openModal('modalExpense');
    }

    setSplitMethod(method, existingSplits = null) {
        this.currentSplitMethod = method;
        const eqBtn = document.getElementById('expSplitEqualBtn');
        const cuBtn = document.getElementById('expSplitCustomBtn');
        const eqCont = document.getElementById('splitEqualContainer');
        const cuCont = document.getElementById('splitCustomContainer');

        if (method === 'EQUAL') {
            eqBtn?.classList.add('active');
            cuBtn?.classList.remove('active');
            if (eqCont) eqCont.style.display = 'block';
            if (cuCont) cuCont.style.display = 'none';

            this.renderEqualParticipants(existingSplits);
        } else {
            cuBtn?.classList.add('active');
            eqBtn?.classList.remove('active');
            if (cuCont) cuCont.style.display = 'block';
            if (eqCont) eqCont.style.display = 'none';

            this.renderCustomParticipants(existingSplits);
        }
    }

    renderEqualParticipants(existingSplits = null) {
        const container = document.getElementById('splitEqualParticipantsList');
        if (!container) return;

        const activeMemberIds = existingSplits ? new Set(existingSplits.map(s => s.memberId)) : null;

        container.innerHTML = this.groupMembers.map(m => {
            const isChecked = activeMemberIds ? activeMemberIds.has(m.id) : true;
            return `
                <label class="split-participant-item" style="cursor:pointer;">
                    <div style="display:flex; align-items:center; gap:10px;">
                        <input type="checkbox" class="eq-participant-chk" value="${m.id}" ${isChecked ? 'checked' : ''} onchange="app.recalcSplitShares()">
                        <span style="font-weight:600; font-size:0.88rem;">${this.escapeHtml(m.displayName)}</span>
                    </div>
                    <span class="eq-share-preview" id="eqSharePreview_${m.id}" style="font-size:0.84rem; color:var(--text-muted); font-weight:700;">₹0.00</span>
                </label>
            `;
        }).join('');

        this.recalcSplitShares();
    }

    renderCustomParticipants(existingSplits = null) {
        const container = document.getElementById('splitCustomParticipantsList');
        if (!container) return;

        const splitMap = {};
        if (existingSplits) {
            existingSplits.forEach(s => splitMap[s.memberId] = s.shareAmount);
        }

        container.innerHTML = this.groupMembers.map(m => {
            const val = splitMap[m.id] !== undefined ? splitMap[m.id] : '';
            return `
                <div class="split-participant-item">
                    <span style="font-weight:600; font-size:0.88rem;">${this.escapeHtml(m.displayName)}</span>
                    <div style="display:flex; align-items:center; gap:6px;">
                        <span style="font-size:0.85rem; color:var(--text-muted);">₹</span>
                        <input type="number" step="0.01" min="0" class="form-control custom-share-input" data-member-id="${m.id}" value="${val}" placeholder="0.00" style="width:110px; padding:6px 10px;" oninput="app.recalcSplitShares()">
                    </div>
                </div>
            `;
        }).join('');

        this.recalcSplitShares();
    }

    recalcSplitShares() {
        const amount = parseFloat(document.getElementById('expAmount')?.value || 0);

        if (this.currentSplitMethod === 'EQUAL') {
            const chks = Array.from(document.querySelectorAll('.eq-participant-chk:checked'));
            const count = chks.length;
            const previewEls = document.querySelectorAll('.eq-share-preview');

            if (count > 0 && amount > 0) {
                const baseShare = Math.floor((amount / count) * 100) / 100;
                previewEls.forEach(el => {
                    const mId = el.id.replace('eqSharePreview_', '');
                    const isChecked = chks.some(c => c.value === mId);
                    el.textContent = isChecked ? this.formatMoney(baseShare) : 'Excluded';
                    el.style.color = isChecked ? 'var(--burgundy)' : 'var(--text-muted)';
                });
            } else {
                previewEls.forEach(el => el.textContent = '₹0.00');
            }

        } else if (this.currentSplitMethod === 'CUSTOM') {
            const inputs = Array.from(document.querySelectorAll('.custom-share-input'));
            let sum = 0;
            inputs.forEach(inp => {
                const v = parseFloat(inp.value || 0);
                sum += v;
            });

            const indicator = document.getElementById('customSplitIndicator');
            const statusText = document.getElementById('customSplitStatusText');
            const totalText = document.getElementById('customSplitTotalText');

            const diff = Math.round((amount - sum) * 100) / 100;

            if (totalText) {
                totalText.textContent = `Allocated: ${this.formatMoney(sum)} / ${this.formatMoney(amount)}`;
            }

            if (Math.abs(diff) < 0.005 && amount > 0) {
                if (indicator) {
                    indicator.className = 'split-validation-indicator matched';
                }
                if (statusText) statusText.textContent = '✓ Perfectly balanced!';
            } else {
                if (indicator) {
                    indicator.className = 'split-validation-indicator unmatched';
                }
                if (statusText) {
                    if (diff > 0) {
                        statusText.textContent = `Remaining to allocate: ${this.formatMoney(diff)}`;
                    } else {
                        statusText.textContent = `Over-allocated by: ${this.formatMoney(Math.abs(diff))}`;
                    }
                }
            }
        }
    }

    async saveExpense() {
        const id = document.getElementById('expId')?.value;
        const description = document.getElementById('expDescription')?.value?.trim();
        const amount = document.getElementById('expAmount')?.value;
        const paidBy = document.getElementById('expPaidBy')?.value;
        const date = document.getElementById('expDate')?.value;
        const notes = document.getElementById('expNotes')?.value?.trim();

        if (!description) {
            this.showToast('Please enter an expense description', 'warning');
            return;
        }
        if (!amount || parseFloat(amount) <= 0) {
            this.showToast('Please enter a valid expense amount', 'warning');
            return;
        }
        if (!paidBy) {
            this.showToast('Please select who paid for this expense', 'warning');
            return;
        }
        if (!date) {
            this.showToast('Please choose an expense date', 'warning');
            return;
        }

        const payload = {
            description,
            totalAmount: parseFloat(amount),
            paidBy: parseInt(paidBy, 10),
            expenseDate: date,
            notes,
            splitType: this.currentSplitMethod
        };

        if (this.currentSplitMethod === 'EQUAL') {
            const chks = Array.from(document.querySelectorAll('.eq-participant-chk:checked'));
            if (chks.length === 0) {
                this.showToast('Select at least one participant for equal split', 'warning');
                return;
            }
            payload.participantIds = chks.map(c => parseInt(c.value, 10));
        } else {
            const inputs = Array.from(document.querySelectorAll('.custom-share-input'));
            let sum = 0;
            const customSplits = [];
            inputs.forEach(inp => {
                const val = parseFloat(inp.value || 0);
                sum += val;
                customSplits.push({
                    memberId: parseInt(inp.getAttribute('data-member-id'), 10),
                    shareAmount: val
                });
            });

            const diff = Math.round((parseFloat(amount) - sum) * 100) / 100;
            if (Math.abs(diff) > 0.005) {
                this.showToast(`Custom shares sum (${this.formatMoney(sum)}) does not equal total amount (${this.formatMoney(amount)})`, 'error');
                return;
            }
            payload.customSplits = customSplits;
        }

        try {
            if (id) {
                await window.api.updateExpense(this.currentGroupId, id, payload);
                this.showToast('Expense updated successfully');
            } else {
                await window.api.addExpense(this.currentGroupId, payload);
                this.showToast('Expense added successfully');
            }
            this.closeModal('modalExpense');
            this.loadGroupDetail(this.currentGroupId);
        } catch (err) {
            this.showToast(err.message, 'error');
        }
    }

    async editExpense(expenseId) {
        try {
            const exp = await window.api.request(`/groups/${this.currentGroupId}/expenses/${expenseId}`);
            this.openExpenseModal(exp);
        } catch (err) {
            this.showToast('Failed to fetch expense: ' + err.message, 'error');
        }
    }

    confirmDeleteExpense(expenseId) {
        const titleEl = document.getElementById('confirmModalTitle');
        const msgEl = document.getElementById('confirmModalMessage');
        const actionBtn = document.getElementById('confirmModalActionBtn');

        titleEl.textContent = 'Delete Expense';
        msgEl.textContent = 'Are you sure you want to delete this shared expense? Member balances will be automatically recalculated.';
        actionBtn.onclick = async () => {
            try {
                await window.api.deleteExpense(this.currentGroupId, expenseId);
                this.closeModal('modalConfirm');
                this.showToast('Expense deleted');
                this.loadGroupDetail(this.currentGroupId);
            } catch (err) {
                this.showToast(err.message, 'error');
            }
        };

        this.openModal('modalConfirm');
    }

    // ========================================================
    // 5. PROFILE / SETTINGS
    // ========================================================
    openEditProfileModal() {
        const input = document.getElementById('modalProfileName');
        if (input) input.value = window.api.displayName;
        this.openModal('modalEditProfile');
    }

    async saveProfileModal() {
        const name = document.getElementById('modalProfileName')?.value?.trim();
        if (!name) {
            this.showToast('Display name cannot be empty', 'warning');
            return;
        }

        try {
            await window.api.updateProfile(name);
            this.updateProfileUI(name);
            this.closeModal('modalEditProfile');
            this.showToast('Profile updated!');
            if (this.currentView === 'group-detail' && this.currentGroupId) {
                this.loadGroupDetail(this.currentGroupId);
            }
        } catch (err) {
            this.showToast(err.message, 'error');
        }
    }

    async saveProfileSettings() {
        const name = document.getElementById('settingsDisplayName')?.value?.trim();
        if (!name) {
            this.showToast('Display name cannot be empty', 'warning');
            return;
        }

        try {
            await window.api.updateProfile(name);
            this.updateProfileUI(name);
            this.showToast('Profile updated successfully');
        } catch (err) {
            this.showToast(err.message, 'error');
        }
    }
}

// Global App Instance
window.app = new SpendWiseApp();
document.addEventListener('DOMContentLoaded', () => {
    window.app.init();
});
