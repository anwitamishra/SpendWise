/**
 * SpendWise API Client
 * Manages guest identity and communication with Spring Boot backend
 */

const API_BASE = '/api';

class ApiClient {
    constructor() {
        this.guestId = localStorage.getItem('spendwise_guest_id');
        this.displayName = localStorage.getItem('spendwise_display_name') || 'Friend';
    }

    async initGuest() {
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
                this.displayName = data.data.displayName;
                localStorage.setItem('spendwise_display_name', this.displayName);
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
            'X-Guest-Id': this.guestId,
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

    // User Profile
    async updateProfile(displayName) {
        const user = await this.request('/users/profile', {
            method: 'PUT',
            body: JSON.stringify({ displayName })
        });
        this.displayName = user.displayName;
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
