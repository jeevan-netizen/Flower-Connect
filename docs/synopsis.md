# FlowerConnect — Project Synopsis

FlowerConnect is a hyperlocal flower marketplace that connects customers with local florists. Customers can discover shops by service area, search catalogs, manage addresses, build multi-vendor carts, place orders, track fulfillment, and submit reviews. Vendors manage profiles, products, inventory, orders, and commission-free point-of-sale sales. Administrators oversee vendor approvals, disputes, refunds, commissions, and settlements.

The system estimates distance from service-area centroids; it does not use GPS or a map service. Payments support cash on delivery and a simulated online gateway outside production; real payment processing is future scope. Notifications combine in-app alerts and email, with SSE and polling for updates. SMS integration and Redis are not part of the current implementation.

The backend uses Java 17, Spring Boot, MySQL, and Flyway. The frontend uses React, TypeScript, and Vite. Role-based access separates customer, florist, and administrator actions.

## Functional modules

1. Authentication
2. Vendor profiles and approval
3. Catalog and inventory
4. Addresses, discovery, and search
5. Cart and checkout
6. Order management
7. Payments and COD
8. Cancellations, refunds, and disputes
9. Notifications and real-time updates
10. Billing and settlements
11. POS
12. Reviews and analytics
