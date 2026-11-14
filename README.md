# SpendWise

### Track your money. Split the moments.

SpendWise is a full-stack personal finance and group expense management web application built using Java, Spring Boot, MySQL, JDBC, HTML, CSS and JavaScript.

It helps individuals manage their income and expenses while making it easier for friends, roommates and groups to track shared spending and split bills.

## Features

### Personal Finance
- Track personal income and expenses.
- Categorise transactions.
- Add, edit and delete transactions.
- View transaction history.
- Search and filter transactions.
- View income, expenses and remaining balance.
- Monitor spending through visual summaries.

### Group Expense Management
- Create groups for trips, roommates, events and outings.
- Invite people using shareable links.
- Join groups without mandatory account registration.
- View group members and shared expense history.
- Add expenses and select who paid.
- Choose participants for each expense.

### Expense Splitting
- Equal splitting between selected participants.
- Custom splitting with manually entered shares.
- Automatic share calculations.
- Validation to ensure custom shares match the total.
- Member-wise balance calculations.
- Clear indication of who owes money and who should receive money.

### User Experience
- Guest-friendly experience.
- Pastel colour palette and clean interface.
- Responsive desktop and mobile layouts.
- Clear forms, validation and feedback.
- Simple navigation and useful empty states.

## Design Philosophy

SpendWise follows a minimal, warm, and editorial visual style.

The interface uses a curated luxury-fintech palette:
- **Espresso Brown** (`#241817`): Deep hero cards, page headings, high-contrast anchors, and footer.
- **Burgundy** (`#4D0E13`): Primary CTA buttons, brand badges, and key action indicators.
- **Champagne / Sand** (`#D8C4AC`): Structural borders, dividers, and secondary accents.
- **Dusty Pink** (`#C8A49F`): Soft highlights, secondary badges, and chart accents.
- **Creme / Warm Ivory** (`#EEE4DA`): Background surfaces, canvas, and soft card fills.

The goal is to create a polished finance product that feels natural, trustworthy, and calm to use.

## Tech Stack

| Component | Technology |
|---|---|
| Frontend | HTML, CSS, JavaScript |
| Backend | Java, Spring Boot |
| Database | MySQL |
| Database Connectivity | JDBC / JdbcTemplate |
| API | REST |
| Build Tool | Maven |
| Charts | Chart.js or equivalent |

## Project Architecture

The project follows a layered architecture:

- **Controller:** Handles HTTP requests and responses.
- **Service:** Contains application logic and expense calculations.
- **DAO/Repository:** Communicates with MySQL using JdbcTemplate.
- **Model:** Contains application data models.
- **Frontend:** Provides the user interface and communicates with REST APIs.

## Project Structure

```text
SpendWise/
│
├── src/
│   └── main/
│       ├── java/com/spendwise/
│       │   ├── controller/
│       │   ├── service/
│       │   ├── repository/
│       │   ├── model/
│       │   └── SpendWiseApplication.java
│       │
│       └── resources/
│           ├── static/
│           │   ├── index.html
│           │   ├── css/
│           │   │   └── style.css
│           │   ├── js/
│           │   │   └── app.js
│           │   └── assets/
│           │
│           └── application.properties
│
├── database/
│   └── schema.sql
│
├── pom.xml
└── README.md
```

The structure may expand as additional controllers, services, repositories and frontend pages are implemented.

## Getting Started

### Prerequisites

- Java 25 (LTS) or later.
- Ensure `JAVA_HOME` points to the Java 25 JDK; the Maven wrapper uses it when set.
- (Optional) MySQL 8 or later if running against a local MySQL server.
- Maven is NOT required to be pre-installed (the project includes `mvnw` and `mvnw.cmd` wrappers).

### Quick Start (Zero-Setup)

The application comes preconfigured with automatic schema initialization and persistent file storage in `./data/spendwisedb`:

```bash
# On Windows:
.\mvnw.cmd spring-boot:run

# On Linux / macOS:
./mvnw spring-boot:run
```

Once started, open your browser and navigate to:
```text
http://localhost:8080
```

You can use SpendWise as a guest or create an account with a username, email, and password. Passwords are stored as BCrypt hashes. Forgot-password links expire after 30 minutes; changing a password from Settings requires an authenticated 6-digit email code that expires after 5 minutes. To enable these email workflows, configure Spring Mail environment variables before starting the app (for example `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, and `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true`). Set `SPENDWISE_MAIL_FROM` and `SPENDWISE_PUBLIC_URL` if needed. Do not commit SMTP credentials.

### Running with MySQL 8

1. Open MySQL and create the database:
   ```sql
   CREATE DATABASE IF NOT EXISTS spendwise;
   ```
2. Import the schema:
   ```sql
   SOURCE database/schema.sql;
   ```
3. Set the credentials in your environment rather than committing them to the configuration file:
   ```powershell
   $env:SPENDWISE_DB_USERNAME = "root"
   $env:SPENDWISE_DB_PASSWORD = "your-password"
   ```
   On macOS or Linux, use `export SPENDWISE_DB_USERNAME=root` and
   `export SPENDWISE_DB_PASSWORD=your-password`.
4. Run with the MySQL profile:
   ```bash
   .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=mysql
   ```

## Database Overview

The database is designed around the following entities:

| Table | Purpose |
|---|---|
| users | Stores guest/user identity information |
| personal_transactions | Stores personal income and expenses |
| expense_groups | Stores created groups |
| group_members | Stores group membership |
| group_expenses | Stores shared expenses |
| expense_splits | Stores individual member shares |
| settlements | Optional settlement tracking |

The database uses relational constraints to maintain consistency between users, groups, expenses and split records.

## Core Expense Calculation

SpendWise calculates each group member's net balance using:

```text
Net Balance = Total Amount Paid - Total Amount Owed
```

- Positive balance: Member should receive money.
- Negative balance: Member owes money.
- Zero balance: Member is settled.

Personal financial summaries are calculated separately from group expenses.

## OOP Concepts Used

The project demonstrates object-oriented programming concepts through its Java backend:

- **Encapsulation:** Models keep data and related methods organised.
- **Abstraction:** Service and repository layers separate implementation details.
- **Inheritance:** Can be used where shared model or service behaviour is required.
- **Polymorphism:** Can be applied through interfaces and their implementations.

## Current Development Status

SpendWise is being developed as a complete working web application.

The intended core functionality includes personal finance tracking, group creation, invitation links, expense splitting and automatic balance calculation.

Features should only be marked as completed after they have been implemented and tested.

## Limitations

- Guest display names do not provide secure identity verification.
- Localhost invitation links are not accessible to other devices over the internet.
- Public group collaboration requires deployment to an accessible server.
- Authentication and advanced account management may be introduced in future versions.

## Future Scope

- Optional user authentication.
- Monthly budgets and spending limits.
- Recurring transactions.
- Advanced analytics and charts.
- Custom expense categories.
- CSV and PDF exports.
- Group settlement tracking.
- Notifications.
- Progressive Web App support.

## Academic Project

SpendWise is developed as a full-stack academic project to demonstrate Java programming, object-oriented programming, database connectivity, REST API development and frontend development.

**Project:** SpendWise  
**Tagline:** Track your money. Split the moments.

**Contributor:** Hanu Verma