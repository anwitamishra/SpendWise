/**
 * SpendWise — Main Frontend Application Logic
 */

class SpendWiseApp {
    constructor() {
        this.currentView = 'dashboard';
        this.currentGroupId = null;
        this.currentGroupTab = 'overview';
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
        this.updateProfileUI(user.displayName, user);
        const resetHash = window.location.hash.match(/^#reset-password\?token=(.+)$/);
        if (resetHash) {
            this.authResetToken = decodeURIComponent(resetHash[1]);
            this.showAuthGate('reset');
        } else if (window.api.token || localStorage.getItem('spendwise_mode') === 'guest') {
            this.hideAuthGate();
        } else {
            this.showAuthGate('login');
        }

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

    updateProfileUI(name, user = window.api.user) {
        const initial = (name && name.length > 0) ? name.charAt(0).toUpperCase() : 'G';
        const avatarEl = document.getElementById('sidebarAvatar');
        const nameEl = document.getElementById('sidebarUserName');
        const settingsNameEl = document.getElementById('settingsDisplayName');
        const settingsIdEl = document.getElementById('settingsGuestId');
        const settingsEmailEl = document.getElementById('settingsEmail');
        const settingsUsernameEl = document.getElementById('settingsUsername');
        const accountStatusEl = document.getElementById('sidebarAccountStatus');
        const accountTypeEl = document.getElementById('settingsAccountType');
        const memberSinceEl = document.getElementById('settingsMemberSince');
        const guestActions = document.getElementById('settingsGuestActions');
        const accountActions = document.getElementById('settingsAccountActions');
        const securityDescription = document.getElementById('settingsSecurityDescription');
        const isAccount = Boolean(user?.email);

        if (avatarEl) avatarEl.textContent = initial;
        if (nameEl) nameEl.textContent = name;
        if (settingsNameEl) settingsNameEl.value = name;
        if (settingsIdEl) settingsIdEl.value = window.api.guestId;
        if (settingsEmailEl) settingsEmailEl.value = user?.email || '';
        if (settingsUsernameEl) settingsUsernameEl.value = user?.username || '';
        if (accountStatusEl) accountStatusEl.textContent = isAccount ? 'Account' : 'Guest Session';
        if (accountTypeEl) accountTypeEl.textContent = isAccount ? 'Registered account' : 'Guest session';
        if (memberSinceEl) {
            const createdValue = user?.createdAt;
            const created = Array.isArray(createdValue)
                ? new Date(createdValue[0], (createdValue[1] || 1) - 1, createdValue[2] || 1)
                : (createdValue ? new Date(createdValue) : null);
            memberSinceEl.textContent = created && !Number.isNaN(created.getTime())
                ? created.toLocaleDateString(undefined, { month: 'short', year: 'numeric' }) : '—';
        }
        if (guestActions) guestActions.hidden = isAccount;
        if (accountActions) accountActions.hidden = !isAccount;
        if (securityDescription) securityDescription.textContent = isAccount
            ? 'Update your password regularly to keep your financial profile protected.'
            : 'Create an account to secure your profile and access it across sessions.';
    }

    showAuthGate(mode = 'login') {
        const panel = document.getElementById(`auth${mode.charAt(0).toUpperCase()}${mode.slice(1)}Panel`);
        if (!panel) mode = 'login';
        document.querySelectorAll('[data-auth-panel]').forEach(item => { item.hidden = true; });
        const activePanel = document.getElementById(`auth${mode.charAt(0).toUpperCase()}${mode.slice(1)}Panel`);
        if (activePanel) activePanel.hidden = false;
        const feedback = document.getElementById('authFeedback');
        if (feedback) { feedback.textContent = ''; feedback.classList.remove('success'); }
        if (mode === 'register') {
            const nameInput = document.getElementById('authRegisterName');
            if (nameInput && !nameInput.value) nameInput.value = window.api.displayName === 'Friend' ? '' : window.api.displayName;
        }
        document.body.classList.add('auth-required');
    }

    hideAuthGate() {
        document.body.classList.remove('auth-required');
    }

    showAuthFeedback(message, success = false) {
        const feedback = document.getElementById('authFeedback');
        if (!feedback) return;
        feedback.textContent = message;
        feedback.classList.toggle('success', success);
    }

    togglePasswordVisibility(inputId, button) {
        const input = document.getElementById(inputId);
        if (!input || !button) return;
        const showing = input.type === 'password';
        input.type = showing ? 'text' : 'password';
        button.textContent = showing ? 'Hide' : 'Show';
        button.setAttribute('aria-label', `${showing ? 'Hide' : 'Show'} password`);
        button.setAttribute('aria-pressed', String(showing));
    }

    async submitLogin(event) {
        event.preventDefault();
        try {
            const user = await window.api.login(
                document.getElementById('authLoginEmail').value,
                document.getElementById('authLoginPassword').value
            );
            this.finishAuthentication(user);
        } catch (err) { this.showAuthFeedback(err.message); }
    }

    async submitRegister(event) {
        event.preventDefault();
        try {
            const user = await window.api.register(
                document.getElementById('authRegisterName').value.trim(),
                document.getElementById('authRegisterUsername').value.trim(),
                document.getElementById('authRegisterEmail').value,
                document.getElementById('authRegisterPassword').value
            );
            this.finishAuthentication(user);
        } catch (err) { this.showAuthFeedback(err.message); }
    }

    async submitForgotPassword(event) {
        event.preventDefault();
        try {
            await window.api.forgotPassword(document.getElementById('authForgotEmail').value);
            this.showAuthFeedback('If an account exists for that email, a reset link has been sent.', true);
        } catch (err) { this.showAuthFeedback(err.message); }
    }

    async submitResetPassword(event) {
        event.preventDefault();
        try {
            await window.api.resetPassword(this.authResetToken, document.getElementById('authResetPassword').value);
            this.showAuthGate('login');
            this.showAuthFeedback('Password updated. Sign in with your new password.', true);
            window.location.hash = '#login';
        } catch (err) { this.showAuthFeedback(err.message); }
    }

    finishAuthentication(user) {
        this.updateProfileUI(user.displayName, user);
        this.hideAuthGate();
        window.location.hash = '#dashboard';
        this.handleHashChange();
        this.showToast('You are signed in.');
    }

    async continueAsGuest() {
        localStorage.setItem('spendwise_mode', 'guest');
        const user = await window.api.initGuest();
        this.updateProfileUI(user.displayName, user);
        this.hideAuthGate();
        window.location.hash = '#dashboard';
        this.handleHashChange();
    }

    openAuthGate(mode = 'login') {
        this.showAuthGate(mode);
    }

    async signOut() {
        try {
            await window.api.logout();
            const guest = await window.api.initGuest();
            this.updateProfileUI(guest.displayName, guest);
            this.showAuthGate('login');
        } catch (err) { this.showToast(err.message, 'error'); }
    }

    async startPasswordChange() {
        this.setPasswordChangeFeedback('');
        try {
            const result = await window.api.requestPasswordChangeOtp();
            document.getElementById('passwordOtpMaskedEmail').textContent = result.maskedEmail;
            document.getElementById('passwordChangeOtp').value = '';
            document.getElementById('passwordChangeFlow').hidden = false;
            document.getElementById('settingsStartPasswordChange').hidden = true;
            document.getElementById('passwordOtpStep').hidden = false;
            document.getElementById('passwordNewPasswordStep').hidden = true;
            this.startPasswordOtpCountdown(result.expiresInSeconds, result.resendAfterSeconds);
        } catch (err) {
            this.showToast(err.message, 'error');
        }
    }

    async resendPasswordChangeOtp() {
        const resendButton = document.getElementById('passwordOtpResend');
        if (resendButton?.disabled) return;
        try {
            const result = await window.api.requestPasswordChangeOtp();
            document.getElementById('passwordOtpMaskedEmail').textContent = result.maskedEmail;
            document.getElementById('passwordChangeOtp').value = '';
            this.setPasswordChangeFeedback('A new verification code has been sent.', true);
            this.startPasswordOtpCountdown(result.expiresInSeconds, result.resendAfterSeconds);
        } catch (err) {
            this.setPasswordChangeFeedback(err.message);
        }
    }

    startPasswordOtpCountdown(expiresInSeconds, resendAfterSeconds) {
        if (this.passwordOtpTimer) clearInterval(this.passwordOtpTimer);
        this.passwordOtpRemaining = expiresInSeconds;
        this.passwordOtpResendRemaining = resendAfterSeconds;
        const resendButton = document.getElementById('passwordOtpResend');
        if (resendButton) resendButton.disabled = this.passwordOtpResendRemaining > 0;
        const renderCountdown = () => {
            const countdown = document.getElementById('passwordOtpCountdown');
            if (countdown) {
                const minutes = Math.floor(this.passwordOtpRemaining / 60);
                const seconds = this.passwordOtpRemaining % 60;
                const expiration = this.passwordOtpRemaining > 0
                    ? `Code expires in ${minutes}:${String(seconds).padStart(2, '0')}.`
                    : 'Code expired. Request a new one.';
                countdown.textContent = this.passwordOtpResendRemaining > 0
                    ? `${expiration} Resend available in ${this.passwordOtpResendRemaining}s.`
                    : `${expiration} You can request another code.`;
            }
            if (resendButton) resendButton.disabled = this.passwordOtpResendRemaining > 0;
        };
        renderCountdown();
        this.passwordOtpTimer = setInterval(() => {
            if (this.passwordOtpRemaining > 0) this.passwordOtpRemaining -= 1;
            if (this.passwordOtpResendRemaining > 0) this.passwordOtpResendRemaining -= 1;
            renderCountdown();
            if (this.passwordOtpRemaining === 0 && this.passwordOtpResendRemaining === 0) {
                clearInterval(this.passwordOtpTimer);
                this.passwordOtpTimer = null;
            }
        }, 1000);
    }

    async verifyPasswordChangeOtp(event) {
        event.preventDefault();
        try {
            await window.api.verifyPasswordChangeOtp(document.getElementById('passwordChangeOtp').value.trim());
            if (this.passwordOtpTimer) clearInterval(this.passwordOtpTimer);
            document.getElementById('passwordOtpStep').hidden = true;
            document.getElementById('passwordNewPasswordStep').hidden = false;
            this.setPasswordChangeFeedback('Email verified. You can now set a new password.', true);
        } catch (err) { this.setPasswordChangeFeedback(err.message); }
    }

    async completePasswordChange(event) {
        event.preventDefault();
        const password = document.getElementById('settingsNewPassword').value;
        const confirmation = document.getElementById('settingsConfirmPassword').value;
        if (password.length < 10 || password.length > 72) {
            this.setPasswordChangeFeedback('Password must be between 10 and 72 characters.');
            return;
        }
        if (password !== confirmation) {
            this.setPasswordChangeFeedback('The password fields do not match.');
            return;
        }
        try {
            const user = await window.api.completePasswordChange(password);
            this.updateProfileUI(user.displayName, user);
            this.resetPasswordChangeFlow();
            this.showToast('Password changed successfully.');
        } catch (err) { this.setPasswordChangeFeedback(err.message); }
    }

    cancelPasswordChange() {
        this.resetPasswordChangeFlow();
    }

    resetPasswordChangeFlow() {
        if (this.passwordOtpTimer) clearInterval(this.passwordOtpTimer);
        this.passwordOtpTimer = null;
        document.getElementById('passwordChangeFlow').hidden = true;
        document.getElementById('settingsStartPasswordChange').hidden = false;
        document.getElementById('passwordOtpStep').hidden = true;
        document.getElementById('passwordNewPasswordStep').hidden = true;
        document.getElementById('passwordChangeCancel').hidden = false;
        document.getElementById('passwordChangeOtp').value = '';
        document.getElementById('passwordNewPasswordStep').querySelector('form')?.reset();
        this.setPasswordChangeFeedback('');
    }

    setPasswordChangeFeedback(message, success = false) {
        const feedback = document.getElementById('passwordChangeFeedback');
        if (!feedback) return;
        feedback.textContent = message;
        feedback.classList.toggle('success', success);
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
                const nextGroupId = parseInt(groupId, 10);
                if (this.currentGroupId !== nextGroupId) this.currentGroupTab = 'overview';
                this.currentGroupId = nextGroupId;
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

    openTransactionModal(editData = null, initialType = null) {
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
            this.setTxType(initialType || 'EXPENSE');
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
            const [group, members, balancesData, expenses, settlements] = await Promise.all([
                window.api.getGroup(groupId),
                window.api.getGroupMembers(groupId),
                window.api.getGroupBalances(groupId),
                window.api.getGroupExpenses(groupId),
                window.api.getSettlements(groupId)
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
            document.getElementById('gdOverviewSpent').textContent = this.formatMoney(totalSpent);
            document.getElementById('gdOverviewMembers').textContent = String(members.length);

            // Render Balances
            this.renderGroupBalances(balancesData.balances || []);

            // Render Settlement Suggestions
            this.renderGroupSettlements(balancesData.settlements || []);

            // Render Expenses List
            this.renderGroupExpenses(expenses || []);
            this.renderTrackedSettlements(settlements || []);
            this.showGroupTab(this.currentGroupTab || 'overview');
            this.renderSmartSplitMethod();
            if (this.currentGroupTab === 'smart-split') this.loadGroupReceipts(groupId);

        } catch (err) {
            this.showToast('Failed to load group details: ' + err.message, 'error');
            this.navigate('groups');
        }
    }

    showGroupTab(tab) {
        const valid = ['overview', 'expenses', 'balances', 'smart-split'];
        this.currentGroupTab = valid.includes(tab) ? tab : 'overview';
        document.querySelectorAll('.group-tab').forEach(button => {
            const active = button.dataset.groupTab === this.currentGroupTab;
            button.classList.toggle('active', active);
            button.setAttribute('aria-selected', String(active));
        });
        document.querySelectorAll('.group-tab-panel').forEach(panel => panel.classList.remove('active'));
        const panelId = `gdTab${this.currentGroupTab.split('-').map(part => part.charAt(0).toUpperCase() + part.slice(1)).join('')}`;
        document.getElementById(panelId)?.classList.add('active');
        if (this.currentGroupTab === 'smart-split' && this.currentGroupId) {
            this.populateSmartSplitPayer();
            this.loadGroupReceipts(this.currentGroupId);
        }
    }

    renderTrackedSettlements(settlements) {
        const container = document.getElementById('gdTrackedSettlements');
        if (!container) return;
        if (!settlements.length) {
            container.innerHTML = '<p class="empty-state-text">No settlement records yet. Generate a plan from the optimized suggestions.</p>';
            return;
        }
        container.innerHTML = settlements.map(settlement => `
            <div class="tracked-settlement-row">
                <div><strong>${this.escapeHtml(settlement.fromMemberName)}</strong><span> pays </span><strong>${this.escapeHtml(settlement.toMemberName)}</strong><small>${settlement.status === 'COMPLETED' ? `Completed ${this.escapeHtml(settlement.settledDate || '')}` : 'Awaiting confirmation'}</small></div>
                <div class="tracked-settlement-actions"><strong>${this.formatMoney(settlement.amount)}</strong>${settlement.status === 'PENDING' ? `<button class="btn btn-primary btn-sm" onclick="app.completeSettlement(${settlement.id})">Mark paid</button>` : '<span class="badge badge-settled">Completed</span>'}</div>
            </div>
        `).join('');
    }

    async generateSettlementPlan() {
        try {
            const settlements = await window.api.generateSettlementPlan(this.currentGroupId);
            this.renderTrackedSettlements(settlements);
            this.showToast('Optimized pending settlement plan updated.');
            this.loadGroupDetail(this.currentGroupId);
        } catch (err) { this.showToast(err.message, 'error'); }
    }

    async completeSettlement(settlementId) {
        try {
            await window.api.completeSettlement(this.currentGroupId, settlementId);
            this.showToast('Settlement marked complete.');
            this.loadGroupDetail(this.currentGroupId);
        } catch (err) { this.showToast(err.message, 'error'); }
    }

    populateSmartSplitPayer() {
        const payer = document.getElementById('smartSplitPayer');
        if (!payer || !this.groupMembers.length) return;
        const previous = payer.value;
        payer.innerHTML = this.groupMembers.map(member => `<option value="${member.id}">${this.escapeHtml(member.displayName)}</option>`).join('');
        if (previous && this.groupMembers.some(member => String(member.id) === previous)) payer.value = previous;
        if (!document.getElementById('smartSplitDate').value) document.getElementById('smartSplitDate').value = new Date().toISOString().split('T')[0];
    }

    renderSmartSplitMethod() {
        const container = document.getElementById('smartSplitAllocations');
        const method = document.getElementById('smartSplitMethod')?.value || 'EQUAL';
        if (!container || !this.groupMembers.length) return;
        const totalInput = document.getElementById('smartSplitAmount');
        const totalLabel = document.querySelector('label[for="smartSplitAmount"]');
        const payerField = document.getElementById('smartSplitPayerField');
        const payerSelect = document.getElementById('smartSplitPayer');
        if (totalInput) totalInput.readOnly = method === 'CONTRIBUTION_EQUAL';
        if (totalLabel) totalLabel.textContent = method === 'CONTRIBUTION_EQUAL' ? 'Calculated total (₹)' : 'Total amount (₹)';
        if (payerField) payerField.hidden = method === 'CONTRIBUTION_EQUAL';
        if (payerSelect) payerSelect.required = method !== 'CONTRIBUTION_EQUAL';
        if (method === 'CONTRIBUTION_EQUAL') {
            container.innerHTML = `<p class="smart-field-label">Enter what each group member paid. SpendWise adds these amounts and shares the total equally among all group members.</p><div class="smart-allocation-list">${this.groupMembers.map(member => `<label class="smart-allocation-row"><span>${this.escapeHtml(member.displayName)} paid</span><div class="smart-allocation-input"><span>₹</span><input class="form-control smart-member-paid" data-member-id="${member.id}" type="number" min="0" step="0.01" value="0" oninput="app.updateSmartSplitPreview()"></div></label>`).join('')}</div>`;
        } else if (method === 'EQUAL') {
            container.innerHTML = `<p class="smart-field-label">Choose who shares this expense</p><div class="smart-member-grid">${this.groupMembers.map(member => `<label class="smart-member-choice"><input class="smart-equal-member" type="checkbox" value="${member.id}" checked><span>${this.escapeHtml(member.displayName)}</span></label>`).join('')}</div>`;
        } else if (method === 'CUSTOM' || method === 'PERCENTAGE') {
            const unit = method === 'PERCENTAGE' ? '%' : '₹';
            const step = method === 'PERCENTAGE' ? '0.0001' : '0.01';
            container.innerHTML = `<p class="smart-field-label">${method === 'PERCENTAGE' ? 'Enter a percentage for each person (must total 100%).' : 'Set each person’s exact share (shares must equal the total).'}</p><div class="smart-allocation-list">${this.groupMembers.map(member => `<label class="smart-allocation-row"><span>${this.escapeHtml(member.displayName)}</span><div class="smart-allocation-input"><input class="form-control smart-member-allocation" data-member-id="${member.id}" type="number" min="0" step="${step}" value="0" oninput="app.updateSmartSplitPreview()"><span>${unit}</span></div></label>`).join('')}</div>`;
        } else {
            container.innerHTML = `<p class="smart-field-label">Add each bill line and select who consumed or shares it. Each line is divided equally among its selected members.</p><div id="smartSplitItems"></div><button class="btn btn-outline btn-sm" type="button" onclick="app.addSmartSplitItemRow()">+ Add bill item</button>`;
            this.addSmartSplitItemRow();
        }
        container.querySelectorAll('input').forEach(input => input.addEventListener('change', () => this.updateSmartSplitPreview()));
        this.updateSmartSplitPreview();
    }

    addSmartSplitItemRow(existingItem = null) {
        const container = document.getElementById('smartSplitItems');
        if (!container) return;
        const memberOptions = this.groupMembers.map(member => `<option value="${member.id}">${this.escapeHtml(member.displayName)}</option>`).join('');
        const row = document.createElement('div');
        row.className = 'smart-item-row';
        row.innerHTML = `<div class="form-row"><div class="form-group"><label class="form-label">Item</label><input class="form-control smart-item-description" maxlength="150" placeholder="e.g. Shared starter" required></div><div class="form-group"><label class="form-label">Amount (₹)</label><input class="form-control smart-item-amount" type="number" min="0.01" step="0.01" placeholder="0.00" oninput="app.updateSmartSplitPreview()" required></div></div><div class="form-group"><label class="form-label">Assigned members</label><select class="form-control form-select smart-item-members" multiple size="${Math.min(4, this.groupMembers.length)}">${memberOptions}</select><small class="auth-hint">Use Command-click (Mac) or Ctrl-click to select multiple members.</small></div><button class="auth-text-button smart-remove-item" type="button">Remove item</button>`;
        row.querySelector('.smart-remove-item').addEventListener('click', () => { row.remove(); this.updateSmartSplitPreview(); });
        row.querySelector('.smart-item-members').addEventListener('change', () => this.updateSmartSplitPreview());
        container.appendChild(row);
        if (existingItem) {
            row.querySelector('.smart-item-description').value = existingItem.description || '';
            row.querySelector('.smart-item-amount').value = existingItem.amount || '';
            const assignedIds = new Set((existingItem.memberIds || []).map(String));
            Array.from(row.querySelector('.smart-item-members').options).forEach(option => { option.selected = assignedIds.has(option.value); });
        }
    }

    updateSmartSplitPreview() {
        const preview = document.getElementById('smartSplitPreview');
        if (!preview) return;
        const total = Number(document.getElementById('smartSplitAmount')?.value || 0);
        const method = document.getElementById('smartSplitMethod')?.value || 'EQUAL';
        let detail = '';
        let detailIsHtml = false;
        if (method === 'CONTRIBUTION_EQUAL') {
            const paidInputs = Array.from(document.querySelectorAll('.smart-member-paid'));
            const paidCentsByMember = new Map(paidInputs.map(input => [Number(input.dataset.memberId), Math.round(Number(input.value || 0) * 100)]));
            const totalCents = Array.from(paidCentsByMember.values()).reduce((sum, cents) => sum + cents, 0);
            const paidTotal = totalCents / 100;
            const amountInput = document.getElementById('smartSplitAmount');
            if (amountInput) amountInput.value = paidTotal.toFixed(2);
            const memberCount = this.groupMembers.length;
            const baseShareCents = memberCount ? Math.floor(totalCents / memberCount) : 0;
            const remainderCents = memberCount ? totalCents % memberCount : 0;
            const debtors = [];
            const creditors = [];
            const memberDetails = this.groupMembers.map((member, index) => {
                const paidCents = paidCentsByMember.get(member.id) || 0;
                const shareCents = baseShareCents + (index < remainderCents ? 1 : 0);
                const netCents = paidCents - shareCents;
                if (netCents < 0) debtors.push({ member, cents: -netCents });
                if (netCents > 0) creditors.push({ member, cents: netCents });
                return `<div>${this.escapeHtml(member.displayName)}: paid ${this.formatMoney(paidCents / 100)} · share ${this.formatMoney(shareCents / 100)}</div>`;
            });
            const transfers = [];
            while (debtors.length && creditors.length) {
                const debtor = debtors[0];
                const creditor = creditors[0];
                const cents = Math.min(debtor.cents, creditor.cents);
                transfers.push(`<div>${this.escapeHtml(debtor.member.displayName)} pays ${this.escapeHtml(creditor.member.displayName)} ${this.formatMoney(cents / 100)}</div>`);
                debtor.cents -= cents;
                creditor.cents -= cents;
                if (debtor.cents === 0) debtors.shift();
                if (creditor.cents === 0) creditors.shift();
            }
            const paymentSummary = totalCents === 0
                ? '<div>Enter paid amounts to calculate who owes whom.</div>'
                : transfers.length ? transfers.join('') : '<div>No payments needed for this split.</div>';
            detail = `<div>${this.formatMoney(paidTotal)} total · split across ${memberCount} members</div><div class="smart-preview-members">${memberDetails.join('')}</div><div class="smart-preview-payments"><strong>Suggested payments</strong>${paymentSummary}</div>`;
            detailIsHtml = true;
        } else if (method === 'EQUAL') {
            const count = document.querySelectorAll('.smart-equal-member:checked').length;
            detail = count ? `${count} member${count === 1 ? '' : 's'} · about ${this.formatMoney(total / count)} each` : 'Select at least one member';
        } else if (method === 'CUSTOM') {
            const allocated = Array.from(document.querySelectorAll('.smart-member-allocation')).reduce((sum, input) => sum + Number(input.value || 0), 0);
            detail = `Allocated ${this.formatMoney(allocated)} of ${this.formatMoney(total)}${Math.abs(allocated - total) < 0.005 ? ' · balanced' : ''}`;
        } else if (method === 'PERCENTAGE') {
            const percentage = Array.from(document.querySelectorAll('.smart-member-allocation')).reduce((sum, input) => sum + Number(input.value || 0), 0);
            detail = `${percentage.toFixed(2)}% allocated of 100%${Math.abs(percentage - 100) < 0.005 ? ' · balanced' : ''}`;
        } else {
            const rows = Array.from(document.querySelectorAll('.smart-item-row'));
            const itemTotal = rows.reduce((sum, row) => sum + Number(row.querySelector('.smart-item-amount')?.value || 0), 0);
            const assigned = rows.filter(row => row.querySelector('.smart-item-members')?.selectedOptions.length).length;
            detail = `${rows.length} item${rows.length === 1 ? '' : 's'} · ${assigned} assigned · ${this.formatMoney(itemTotal)} of ${this.formatMoney(total)}`;
        }
        if (detailIsHtml) preview.innerHTML = detail;
        else preview.textContent = detail;
    }

    async saveSmartSplit(event) {
        event.preventDefault();
        const method = document.getElementById('smartSplitMethod').value;
        const amount = Number(document.getElementById('smartSplitAmount').value);
        const payload = {
            description: document.getElementById('smartSplitDescription').value.trim(),
            totalAmount: amount,
            paidBy: Number(document.getElementById('smartSplitPayer').value),
            expenseDate: document.getElementById('smartSplitDate').value,
            notes: document.getElementById('smartSplitNotes').value.trim(),
            splitType: method
        };
        if (method === 'CONTRIBUTION_EQUAL') {
            payload.contributions = Array.from(document.querySelectorAll('.smart-member-paid')).map(input => ({
                memberId: Number(input.dataset.memberId),
                amountPaid: Number(input.value || 0)
            }));
            delete payload.paidBy;
        } else if (method === 'EQUAL') {
            payload.participantIds = Array.from(document.querySelectorAll('.smart-equal-member:checked')).map(input => Number(input.value));
        } else if (method === 'CUSTOM' || method === 'PERCENTAGE') {
            const key = method === 'CUSTOM' ? 'customSplits' : 'percentages';
            payload[key] = Array.from(document.querySelectorAll('.smart-member-allocation')).map(input => ({ memberId: Number(input.dataset.memberId), shareAmount: Number(input.value || 0) }));
        } else {
            payload.items = Array.from(document.querySelectorAll('.smart-item-row')).map(row => ({
                description: row.querySelector('.smart-item-description').value.trim(),
                amount: Number(row.querySelector('.smart-item-amount').value),
                memberIds: Array.from(row.querySelector('.smart-item-members').selectedOptions).map(option => Number(option.value))
            }));
        }
        try {
            const expenseId = document.getElementById('smartSplitExpenseId').value;
            if (expenseId) {
                await window.api.updateExpense(this.currentGroupId, expenseId, payload);
                this.showToast('Shared expense updated.');
            } else {
                await window.api.createSmartSplit(this.currentGroupId, payload);
                this.showToast('Shared expense split and saved.');
            }
            document.getElementById('smartSplitForm').reset();
            document.getElementById('smartSplitExpenseId').value = '';
            document.getElementById('smartSplitDate').value = new Date().toISOString().split('T')[0];
            document.getElementById('smartSplitMethod').value = 'EQUAL';
            this.renderSmartSplitMethod();
            this.loadGroupDetail(this.currentGroupId);
        } catch (err) { this.showToast(err.message, 'error'); }
    }

    async uploadGroupReceipt(event) {
        event.preventDefault();
        const input = document.getElementById('groupReceiptFile');
        if (!input.files?.length) return;
        try {
            await window.api.uploadGroupReceipt(this.currentGroupId, input.files[0]);
            input.value = '';
            this.showToast('Receipt saved for future expense extraction.');
            this.loadGroupReceipts(this.currentGroupId);
        } catch (err) { this.showToast(err.message, 'error'); }
    }

    async loadGroupReceipts(groupId) {
        try {
            const receipts = await window.api.getGroupReceipts(groupId);
            const container = document.getElementById('groupReceiptList');
            if (!container) return;
            container.innerHTML = receipts.length ? receipts.map(receipt => `<div class="receipt-list-item"><span class="receipt-file-icon">${receipt.contentType === 'application/pdf' ? 'PDF' : 'IMG'}</span><div><strong>${this.escapeHtml(receipt.originalFilename)}</strong><small>${(receipt.fileSize / 1024).toFixed(0)} KB · saved privately</small></div></div>`).join('') : '<p class="empty-state-text">No receipts uploaded yet.</p>';
        } catch (err) { this.showToast('Could not load receipt list: ' + err.message, 'error'); }
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
            const itemSummary = (exp.items || []).map(item => {
                const memberNames = (item.memberIds || []).map(id => this.groupMembers.find(member => member.id === id)?.displayName || 'Member').join(', ');
                return `<span class="expense-item-chip"><strong>${this.escapeHtml(item.description)}</strong> ${this.formatMoney(item.amount)} · ${this.escapeHtml(memberNames)}</span>`;
            }).join('');
            const payerSummary = (exp.contributions || []).length
                ? (exp.contributions || []).map(contribution => `<div class="expense-payer-chip">${this.escapeHtml(contribution.memberName)}: ${this.formatMoney(contribution.amountPaid)}</div>`).join('')
                : `<div style="font-weight:600; color:var(--burgundy); font-size:0.88rem;">${this.escapeHtml(exp.paidByName || 'Member')}</div>`;

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
                        ${payerSummary}
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
                        ${itemSummary ? `<div class="expense-item-summary">${itemSummary}</div>` : ''}
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
            this.openSmartSplitEditor(exp);
        } catch (err) {
            this.showToast('Failed to fetch expense: ' + err.message, 'error');
        }
    }

    openSmartSplitEditor(expense) {
        this.showGroupTab('smart-split');
        document.getElementById('smartSplitExpenseId').value = expense.id;
        document.getElementById('smartSplitDescription').value = expense.description || '';
        document.getElementById('smartSplitAmount').value = expense.totalAmount;
        document.getElementById('smartSplitPayer').value = expense.paidBy;
        document.getElementById('smartSplitDate').value = expense.expenseDate;
        document.getElementById('smartSplitNotes').value = expense.notes || '';
        document.getElementById('smartSplitMethod').value = expense.splitType || 'EQUAL';
        this.renderSmartSplitMethod();
        if (expense.splitType === 'EQUAL') {
            const included = new Set((expense.splits || []).map(split => String(split.memberId)));
            document.querySelectorAll('.smart-equal-member').forEach(input => { input.checked = included.has(input.value); });
        } else if (expense.splitType === 'CUSTOM') {
            const shares = new Map((expense.splits || []).map(split => [String(split.memberId), split.shareAmount]));
            document.querySelectorAll('.smart-member-allocation').forEach(input => { input.value = shares.get(input.dataset.memberId) ?? 0; });
        } else if (expense.splitType === 'PERCENTAGE') {
            const total = Number(expense.totalAmount || 0);
            const shares = expense.splits || [];
            let assigned = 0;
            document.querySelectorAll('.smart-member-allocation').forEach((input, index) => {
                const share = shares.find(item => String(item.memberId) === input.dataset.memberId);
                let percentage = share && total ? Number(share.shareAmount) / total * 100 : 0;
                if (index === this.groupMembers.length - 1) percentage = Math.max(0, 100 - assigned);
                percentage = Math.round(percentage * 10000) / 10000;
                assigned += percentage;
                input.value = percentage;
            });
        } else if (expense.splitType === 'CONTRIBUTION_EQUAL') {
            const payments = new Map((expense.contributions || []).map(item => [String(item.memberId), item.amountPaid]));
            document.querySelectorAll('.smart-member-paid').forEach(input => { input.value = payments.get(input.dataset.memberId) ?? 0; });
        } else if (expense.splitType === 'ITEMIZED') {
            const container = document.getElementById('smartSplitItems');
            container.innerHTML = '';
            (expense.items || []).forEach(item => this.addSmartSplitItemRow(item));
            if (!expense.items?.length) this.addSmartSplitItemRow();
        }
        this.updateSmartSplitPreview();
        this.showToast('Edit the split details and save your changes.');
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
            const user = await window.api.updateProfile(name);
            this.updateProfileUI(name, user);
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
            const user = await window.api.updateProfile(name);
            this.updateProfileUI(name, user);
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
