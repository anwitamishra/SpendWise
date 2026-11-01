# SpendWise
### Product Requirements Document (PRD)
**Version:** 2.0  
**Product Type:** Full-Stack Personal Finance & Group Expense Management Web Application  
**Tagline:** Track your money. Split the moments.

---

## 1. Product Overview

SpendWise is a web-based application that helps people manage their personal finances and shared expenses in one place.

Users should be able to:
- Track their personal income and expenses.
- Organise transactions into categories.
- Create groups for trips, roommates, events, or everyday shared expenses.
- Invite friends through shareable links.
- Record group expenses and choose how they should be divided.
- See who owes whom and how much.

The application should feel like a practical product that people would genuinely use, not a college-project template or an AI-generated dashboard.

**Core principle:** Keep expense tracking simple, make group splitting transparent, and avoid unnecessary complexity.

## 2. Product Goals

1. Provide a simple personal finance tracker.
2. Make shared expense management easy.
3. Allow users to participate without mandatory account creation.
4. Support both equal and custom expense splitting.
5. Clearly display balances and outstanding amounts.
6. Create a polished, responsive interface suitable for everyday use.

## 3. Target Users

- Students managing monthly expenses.
- Individuals tracking income and spending.
- Friends travelling together.
- Roommates sharing rent, groceries, and bills.
- Groups organising events or outings.

## 4. Design and Visual Direction

The visual design is a major requirement. Do not use a generic SaaS dashboard template.

### Colour scheme
Use a soft, elegant pastel colour palette:
- Warm off-white or ivory for the main background.
- Muted sage green for primary actions and positive financial indicators.
- Soft lavender for secondary elements.
- Pale peach or muted coral for highlights.
- Light blue for informational elements.
- Dark charcoal or deep grey for readable text.

Avoid:
- Excessive gradients.
- Neon colours.
- Pure black backgrounds.
- Overly saturated colours.
- Excessive glassmorphism.
- Unnecessary glowing effects.

### UI style
- Minimal, clean, modern and welcoming.
- Rounded cards with subtle borders and soft shadows.
- Consistent spacing and typography.
- Clear visual hierarchy.
- Simple icons with consistent styling.
- Subtle hover and transition effects.
- Charts should be clean and easy to understand.
- Use whitespace effectively instead of filling every section.

### Overall design inspiration
The interface should feel like a thoughtfully designed modern finance product: approachable, calm, organised, and trustworthy.

Use a consistent design system across every page. Avoid the typical AI-generated appearance of oversized headings, random gradients, excessive cards, meaningless statistics, and repetitive layouts.

## 5. Application Structure

The application should have the following main sections:

1. Dashboard
2. Personal Transactions
3. Groups
4. Group Details
5. Profile / Settings

Use a consistent navigation system. On desktop, a sidebar is preferred. On mobile, use a compact navigation layout.

### 5.1 Dashboard

The dashboard should give users a quick overview of their financial activity.

Display:
- Total income.
- Total expenses.
- Current balance.
- Recent transactions.
- Spending by category.
- Monthly income vs expense overview.
- Active groups.
- Pending group balances, where applicable.

Requirements:
- Use actual database values.
- Allow users to switch between relevant time periods.
- Include clear empty states for new users.
- Clicking a summary item should navigate to the relevant section.
- Avoid fake or hardcoded statistics.

### 5.2 Personal Finance Management

Users should be able to manage their own income and expenses independently of groups.

**Transaction fields:**
- Transaction title.
- Amount.
- Type: Income or Expense.
- Category.
- Date.
- Optional description.

**Required functionality:**
- Add transactions.
- View transaction history.
- Edit existing transactions.
- Delete transactions with confirmation.
- Filter by income or expense.
- Filter by category.
- Filter by date or date range.
- Search by transaction title.
- Sort by date or amount.

**Suggested categories:**

Expenses:
- Food
- Transport
- Shopping
- Education
- Entertainment
- Bills
- Health
- Other

Income:
- Salary
- Allowance
- Freelance
- Scholarship
- Other

Allow users to select an existing category. Custom categories can be considered as an additional feature.

**Financial calculations:**
- Total income = sum of income transactions.
- Total expenses = sum of expense transactions.
- Personal balance = total income - total expenses.

Do not mix personal transactions with group expenses in these calculations.

### 5.3 Group Expense Management

Users should be able to create groups for shared expenses.

Examples:
- Goa Trip
- College Friends
- Apartment Expenses
- Weekend Outing
- Birthday Celebration

**Creating a group:**
- Group name.
- Optional description.
- Creator's display name.
- Automatically generated unique invitation code.
- Shareable invitation URL.

**Joining a group:**
- Users can join using an invitation link or code.
- Ask for a display name when joining.
- Show the group name before confirming entry.
- Prevent duplicate membership where possible.
- Show a useful error if the link or code is invalid.

No mandatory account creation should be required for the basic experience.

### 5.4 Group Details Page

Each group should have its own dedicated page.

Display:
- Group name and description.
- List of members.
- Total group spending.
- Recent group expenses.
- Individual member balances.
- Add expense action.
- Share invitation link.
- Clear indication of who owes money and who should receive money.

Users should be able to copy the invitation link easily.

### 5.5 Adding Group Expenses

**Expense fields:**
- Expense title or description.
- Total amount.
- Person who paid.
- Date.
- Optional notes.
- Participants included in the expense.
- Split type.

The person adding the expense should be able to choose the payer and the participants involved.

### 5.6 Expense Splitting

Two splitting methods are required.

**A. Equal Split**
- Divide the total amount equally among selected participants.
- Automatically calculate each participant's share.
- Handle rounding accurately to two decimal places.
- Ensure the total of all shares equals the original expense.

**B. Custom Split**
- Allow the user to manually enter each participant's share.
- Validate that the sum of all custom shares equals the total expense.
- Display an error if the values do not match.
- Prevent negative or invalid amounts.

Users should be able to review the split before saving the expense.

### 5.7 Group Balances

The application must calculate balances automatically.

For each member:

**Net balance = Amount paid - Amount owed**

- Positive balance: The member should receive money.
- Negative balance: The member owes money.
- Zero balance: The member is settled.

Requirements:
- Calculate balances using actual expense and split records.
- Update balances when an expense is added, edited, or deleted.
- Show clear member-wise balances.
- Display who owes whom and the amount.
- Avoid duplicate or contradictory balance calculations.

The application may also display simplified settlement suggestions to reduce the number of payments between members. These should be suggestions, not actual money transfers.

### 5.8 Expense History

Each group should maintain an expense history.

Display:
- Expense description.
- Amount.
- Payer.
- Participants.
- Individual shares.
- Date.
- Split method.

Allow authorised group members to edit or delete expenses, with appropriate confirmation and recalculation of balances.

## 6. User Experience Requirements

### Guest access
- No mandatory registration or login.
- Ask for a display name when a user first participates.
- Remember the display name on the same browser.
- Use a unique guest/session identifier where possible.
- Do not treat a display name alone as secure authentication.

### Empty states
Provide useful messages when:
- No personal transactions exist.
- No groups have been created.
- A group has no expenses.
- No search results are found.

Each empty state should explain what the user can do next and provide a relevant action.

### Feedback
- Show success messages after saving.
- Show clear validation errors.
- Ask for confirmation before destructive actions.
- Display loading indicators where necessary.
- Avoid silent failures.

### Responsive design
The application must work on:
- Desktop computers.
- Laptops.
- Tablets.
- Mobile phones.

All tables, forms, charts, navigation and group pages must adapt to smaller screens.

## 7. Technical Requirements

### Frontend
- HTML5
- CSS3
- JavaScript
- Responsive layouts
- Fetch API for backend communication
- Chart.js or a similar lightweight charting library if charts are required.

### Backend
- Java 25 (LTS) or later.
- Spring Boot.
- Spring Web for REST APIs.
- Spring JDBC / JdbcTemplate for database operations.
- Maven for dependency management.

Use JDBC/JdbcTemplate rather than relying on an ORM as the primary database interaction method.

### Database
- MySQL 8 or later.
- Proper primary and foreign keys.
- Appropriate indexes.
- Data validation.
- Transactional operations for multi-step expense creation and splitting.

### Architecture

Use a clean layered architecture:

1. **Controller Layer:** Handles HTTP requests and responses.
2. **Service Layer:** Contains business logic and calculations.
3. **DAO/Repository Layer:** Executes database operations using JdbcTemplate.
4. **Model Layer:** Contains Java data models.
5. **Frontend Layer:** Handles UI and user interactions.

Keep business logic out of controllers and frontend code.

## 8. Database Requirements

Suggested tables:

### users
- id
- display_name
- guest_identifier
- created_at

### personal_transactions
- id
- user_id
- title
- amount
- transaction_type
- category
- description
- transaction_date
- created_at

### expense_groups
- id
- group_name
- description
- invite_code
- created_by
- created_at

### group_members
- id
- group_id
- user_id
- display_name
- joined_at

### group_expenses
- id
- group_id
- description
- total_amount
- paid_by
- split_type
- expense_date
- notes
- created_at

### expense_splits
- id
- expense_id
- member_id
- share_amount

Use appropriate relationships and constraints. Invitation codes must be unique. Monetary values should use DECIMAL rather than floating-point database types.

A settlements table may be added if settlement tracking is implemented.

## 9. API Requirements

Create REST endpoints for:

**Personal transactions**
- Create transaction.
- Get all transactions.
- Get transaction by ID.
- Update transaction.
- Delete transaction.
- Retrieve transaction summaries.

**Groups**
- Create group.
- Join group using invitation code.
- Get user's groups.
- Get group details.
- Get group members.
- Retrieve invitation details.

**Group expenses**
- Create expense.
- Get group expenses.
- Get expense details.
- Update expense.
- Delete expense.
- Get member balances.
- Get settlement suggestions.

Use appropriate HTTP methods and status codes. Return consistent JSON responses and meaningful error messages.

## 10. Security and Data Integrity

Even without mandatory accounts:
- Validate all incoming data on the backend.
- Never trust frontend-calculated amounts or balances.
- Verify group membership before allowing group operations.
- Use parameterised SQL queries.
- Avoid exposing database credentials.
- Do not allow arbitrary users to edit unrelated personal transactions.
- Protect invitation codes against accidental duplication.
- Use database transactions when creating expenses and their split records.
- Ensure deleting or editing an expense updates all related data correctly.

Guest access has limitations. Make it clear that display names are not secure identity verification. For a public deployment, use a suitable guest-session or identity mechanism and restrict access to personal records.

## 11. Important Edge Cases

Antigravity must handle:
- Empty transactions and groups.
- Invalid invitation links.
- Duplicate group joins.
- Zero, negative, or invalid expense amounts.
- Custom splits that do not add up.
- Rounding differences in equal splits.
- Expenses involving only selected members.
- Editing or deleting an expense.
- A member leaving a group with existing expenses.
- A payer who is not included as a participant.
- Multiple users attempting to join or modify data.
- Network or database errors.

Do not silently ignore invalid input.

## 12. Acceptance Criteria

The project is considered complete when:

1. A user can open the website without mandatory registration.
2. A user can add, edit, view and delete personal income and expenses.
3. The dashboard displays accurate financial summaries.
4. A user can create a group.
5. Another user can join that group through a working invitation link.
6. Group members can add shared expenses.
7. Equal splitting works correctly.
8. Custom splitting works correctly and validates the total.
9. Member balances are calculated accurately.
10. Editing or deleting an expense recalculates balances.
11. Search and filtering work.
12. The application is responsive on desktop and mobile.
13. Data persists in MySQL after restarting the application.
14. Forms, buttons, links and navigation perform their intended actions.
15. The application handles invalid inputs and errors gracefully.

## 13. Development Instructions for Antigravity

Build the complete working application, not just a frontend prototype.

- Create the complete Spring Boot backend and frontend.
- Connect all functionality to MySQL.
- Implement real CRUD operations.
- Make every visible button and form functional.
- Use actual database data instead of placeholder statistics.
- Implement the calculations in the backend.
- Include SQL schema and setup instructions.
- Keep the code organised and maintainable.
- Test the main user flows and fix errors before considering the project complete.
- Do not leave core functionality as TODO comments or static mockups.
- Use sensible defaults where details are unspecified.
- Do not add unnecessary features that distract from the core experience.

## 14. Future Scope

Possible future improvements:
- Optional account registration and authentication.
- Monthly budgets.
- Spending insights and analytics.
- Recurring transactions.
- Custom categories.
- CSV/PDF export.
- Group settlement tracking.
- Notifications.
- More advanced financial charts.
- Progressive Web App support.

These should not take priority over completing the core requirements.

---

**Final Product Vision:** SpendWise should be a clean, reliable and genuinely useful finance tool. A new user should understand how to track an expense or split a bill within seconds, without needing a tutorial.