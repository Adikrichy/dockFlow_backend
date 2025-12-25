package org.aldousdev.dockflowbackend.auth.validators;

public class PasswordValidator {
    
    private static final String PASSWORD_PATTERN = 
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{12,}$";
    
    public static boolean isValid(String password) {
        if (password == null) {
            return false;
        }
        return password.matches(PASSWORD_PATTERN);
    }
    
    public static String getRequirements() {
        return "Password must be at least 12 characters and contain: " +
               "- At least one lowercase letter (a-z), " +
               "- At least one uppercase letter (A-Z), " +
               "- At least one digit (0-9), " +
               "- At least one special character (@$!%*?&)";
    }
}
