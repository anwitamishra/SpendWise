/**
 * SpendWise API Client
 * Manages guest identity and communication with Spring Boot backend
 */

const API_BASE = '/api';

class ApiClient {
    constructor() {
        this.guestId = localStorage.getItem('spendwise_guest_id');
        this.displayName = localStorage.getItem('spendwise_display_name') || 'Friend';
        this.token = localStorage.getItem('spendwise_auth_token');
        this.user = null;
    }

    async initGuest() {
        if (this.token) {
            try {
                const user = await this.request('/auth/me');
                this.setUser(user);
                return user;
            } catch (err) {
                this.clearAuth();
                this.guestId = this.generateUUID();
                this.displayName = 'Friend';
                localStorage.setItem('spendwise_guest_id', this.guestId);
                localStorage.setItem('spendwise_display_name', this.displayName);
            }
        }
        if (!this.guestId) {
            this.guestId = this.generateUUID();
            localStorage.setItem('spendwise_guest_id', this.guestId);
        }

        try {
            const res = await fetch(`${API_BASE}/users/guest`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    guestIdentifier: this.guestId,
                    displayName: this.displayName
                })
            });
            const data = await res.json();
            if (data.success && data.data) {
                this.setUser(data.data);
                return data.data;
            }
        } catch (err) {
            console.error('Failed to initialize guest session', err);
        }
        return { guestIdentifier: this.guestId, displayName: this.displayName };
    }

    generateUUID() {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
            const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
    }

    async request(endpoint, options = {}) {
        const headers = {
            'Content-Type': 'application/json',
            ...(this.guestId ? { 'X-Guest-Id': this.guestId } : {}),
            ...(this.token ? { Authorization: `Bearer ${this.token}` } : {}),
            ...(options.headers || {})
        };

        const config = {
            ...options,
            headers
        };

        const response = await fetch(`${API_BASE}${endpoint}`, config);
        const json = await response.json().catch(() => ({
            success: false,
            message: 'Invalid response from server'
        }));

        if (!response.ok || !json.success) {
            const errorMsg = json.message || `Request failed with status ${response.status}`;
            throw new Error(errorMsg);
        }

        return json.data;
    }

    setUser(user) {
        this.user = user;
        this.guestId = user.guestIdentifier;
        this.displayName = user.displayName;
        localStorage.setItem('spendwise_guest_id', this.guestId);
        localStorage.setItem('spendwise_display_name', this.displayName);
    }

    setAuth(result) {
        this.token = result.token;
        localStorage.setItem('spendwise_auth_token', this.token);
        localStorage.setItem('spendwise_mode', 'account');
        this.setUser(result.user);
    }

    clearAuth() {
        this.token = null;
        this.user = null;
        localStorage.removeItem('spendwise_auth_token');
    }

    async login(identifier, password) {
        const result = await this.authRequest('/login', { identifier, password });
        this.setAuth(result);
        return result.user;
    }

    async register(displayName, username, email, password) {
        const result = await this.authRequest('/register', {
            displayName,
            username,
            email,
            password,
            guestIdentifier: this.guestId
        });
        this.setAuth(result);
        return result.user;
    }

    async forgotPassword(email) {
        return this.authRequest('/forgot-password', { email });
    }

    async resetPassword(token, newPassword) {
        return this.authRequest('/reset-password', { token, newPassword });
    }

    async requestPasswordChangeOtp() {
        return this.request('/auth/password-change/request', { method: 'POST' });
    }

    async verifyPasswordChangeOtp(otp) {
        return this.request('/auth/password-change/verify', {
            method: 'POST',
            body: JSON.stringify({ otp })
        });
    }

    async completePasswordChange(newPassword) {
        const result = await this.request('/auth/password-change/complete', {
            method: 'POST',
            body: JSON.stringify({ newPassword })
        });
        this.setAuth(result);
        return result.user;
    }

    async logout() {
        try {
            await this.request('/auth/logout', { method: 'POST' });
        } finally {
            this.clearAuth();
            this.guestId = this.generateUUID();
            this.displayName = 'Friend';
            localStorage.setItem('spendwise_guest_id', this.guestId);
            localStorage.setItem('spendwise_display_name', this.displayName);
            localStorage.removeItem('spendwise_mode');
        }
    }

    async authRequest(path, body) {
        const response = await fetch(`${API_BASE}/auth${path}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        const json = await response.json().catch(() => ({ success: false, message: 'Invalid response from server' }));
        if (!response.ok || !json.success) throw new Error(json.message || 'Request failed');
        return json.data;
    }

    // User Profile
    async updateProfile(displayName) {
        const user = await this.request('/users/profile', {
            method: 'PUT',
            body: JSON.stringify({ displayName })
        });
        this.displayName = user.displayName;
        if (this.user) this.user.displayName = user.displayName;
        localStorage.setItem('spendwise_display_name', this.displayName);
        return user;
    }

    // Dashboard
    async getDashboardSummary(period = 'ALL') {
        return this.request(`/dashboard/summary?period=${encodeURIComponent(period)}`);
    }

    // Personal Transactions
    async getTransactions(params = {}) {
        const query = new URLSearchParams();
        if (params.type) query.append('type', params.type);
        if (params.category) query.append('category', params.category);
        if (params.startDate) query.append('startDate', params.startDate);
        if (params.endDate) query.append('endDate', params.endDate);
        if (params.search) query.append('search', params.search);
        if (params.sortBy) query.append('sortBy', params.sortBy);
        if (params.sortDirection) query.append('sortDirection', params.sortDirection);

        const qs = query.toString() ? `?${query.toString()}` : '';
        return this.request(`/transactions${qs}`);
    }

    async createTransaction(transactionData) {
        return this.request('/transactions', {
            method: 'POST',
            body: JSON.stringify(transactionData)
        });
    }

    async updateTransaction(id, transactionData) {
        return this.request(`/transactions/${id}`, {
            method: 'PUT',
            body: JSON.stringify(transactionData)
        });
    }

    async deleteTransaction(id) {
        return this.request(`/transactions/${id}`, {
            method: 'DELETE'
        });
    }

    // Groups
    async getGroups() {
        return this.request('/groups');
    }

    async createGroup(groupData) {
        return this.request('/groups', {
            method: 'POST',
            body: JSON.stringify(groupData)
        });
    }

    async getGroup(id) {
        return this.request(`/groups/${id}`);
    }

    async getGroupMembers(groupId) {
        return this.request(`/groups/${groupId}/members`);
    }

    async previewGroup(inviteCode) {
        return this.request(`/groups/join/${encodeURIComponent(inviteCode)}`);
    }

    async joinGroup(inviteCode) {
        return this.request('/groups/join', {
            method: 'POST',
            body: JSON.stringify({ inviteCode })
        });
    }

    async getGroupBalances(groupId) {
        return this.request(`/groups/${groupId}/balances`);
    }

    async getSettlements(groupId) {
        return this.request(`/groups/${groupId}/settlements`);
    }

    async generateSettlementPlan(groupId) {
        return this.request(`/groups/${groupId}/settlements/suggestions`, { method: 'POST' });
    }

    async completeSettlement(groupId, settlementId) {
        return this.request(`/groups/${groupId}/settlements/${settlementId}/complete`, { method: 'POST' });
    }

    async cancelSettlement(groupId, settlementId) {
        return this.request(`/groups/${groupId}/settlements/${settlementId}`, { method: 'DELETE' });
    }

    // Group Expenses
    async getGroupExpenses(groupId) {
        return this.request(`/groups/${groupId}/expenses`);
    }

    async addExpense(groupId, expenseData) {
        return this.request(`/groups/${groupId}/expenses`, {
            method: 'POST',
            body: JSON.stringify(expenseData)
        });
    }

    async createSmartSplit(groupId, expenseData) {
        return this.request(`/groups/${groupId}/expenses/smart-split`, {
            method: 'POST',
            body: JSON.stringify(expenseData)
        });
    }

    async getGroupReceipts(groupId) {
        return this.request(`/groups/${groupId}/receipts`);
    }

    async uploadGroupReceipt(groupId, file) {
        const formData = new FormData();
        formData.append('file', file);
        const response = await fetch(`${API_BASE}/groups/${groupId}/receipts`, {
            method: 'POST',
            headers: {
                ...(this.guestId ? { 'X-Guest-Id': this.guestId } : {}),
                ...(this.token ? { Authorization: `Bearer ${this.token}` } : {})
            },
            body: formData
        });
        const json = await response.json().catch(() => ({ success: false, message: 'Invalid response from server' }));
        if (!response.ok || !json.success) throw new Error(json.message || `Upload failed (${response.status})`);
        return json.data;
    }

    async updateExpense(groupId, expenseId, expenseData) {
        return this.request(`/groups/${groupId}/expenses/${expenseId}`, {
            method: 'PUT',
            body: JSON.stringify(expenseData)
        });
    }

    async deleteExpense(groupId, expenseId) {
        return this.request(`/groups/${groupId}/expenses/${expenseId}`, {
            method: 'DELETE'
        });
    }
}

window.api = new ApiClient();
