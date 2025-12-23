package dopaminelite.payment_portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Payment Portal Service.
 * This service provides REST APIs for managing payment portals and student payment submissions.
 * 
 * <p>Key features:
 * <ul>
 *   <li>Create and manage payment portals by month/year</li>
 *   <li>Submit and track payment proofs from students</li>
 *   <li>Approve or reject payment submissions</li>
 *   <li>Export submission data in various formats (CSV, XLSX, PDF)</li>
 * </ul>
 */
@SpringBootApplication
public class PaymentPortalApplication {

	/**
	 * Application entry point.
	 *
	 * @param args command line arguments
	 */
	public static void main(String[] args) {
		SpringApplication.run(PaymentPortalApplication.class, args);
	}

}
