
package com.example.twitterclient.ui.auth;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.twitterclient.MainActivity;
import com.example.twitterclient.R;
import com.example.twitterclient.auth.SessionManager;
import com.example.twitterclient.repository.AuthRepository;

import org.json.JSONObject;

public class LoginFragment extends Fragment {
    private static final String BASE_URL = "http://10.0.2.2:3000";

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(
                R.layout.fragment_login,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        EditText username =
                view.findViewById(R.id.inputUsername);

        EditText password =
                view.findViewById(R.id.inputPassword);

        Button login =
                view.findViewById(R.id.buttonLogin);

        View register = view.findViewById(R.id.buttonGoRegister);
        View welcomePanel = view.findViewById(R.id.welcomePanel);
        View loginPanel = view.findViewById(R.id.loginPanel);
        view.findViewById(R.id.buttonShowLogin).setOnClickListener(v -> {
            welcomePanel.setVisibility(View.GONE);
            loginPanel.setVisibility(View.VISIBLE);
        });
        view.findViewById(R.id.buttonBack).setOnClickListener(v -> {
            loginPanel.setVisibility(View.GONE);
            welcomePanel.setVisibility(View.VISIBLE);
        });
        view.findViewById(R.id.buttonGoogle).setOnClickListener(v ->
                Toast.makeText(requireContext(), "Google sign-in is not available yet", Toast.LENGTH_SHORT).show());
        view.findViewById(R.id.buttonForgotPassword).setOnClickListener(v -> showForgotPasswordDialog());

        AuthRepository repository =
                new AuthRepository(requireContext());

        SessionManager session =
                new SessionManager(requireContext());

        login.setOnClickListener(v -> {

            String user = username.getText()
                    .toString().trim();

            String pass = password.getText()
                    .toString();

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(
                        requireContext(),
                        "Enter username and password",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            login.setEnabled(false);

            repository.login(
                    user,
                    pass,
                    response -> {
                        if (!isAdded()) return;
                        login.setEnabled(true);

                        try {
                            String token =
                                    response.getString("token");

                            JSONObject userData =
                                    response.getJSONObject("user");

                            String actualUsername =
                                    userData.getString("username");

                            session.saveSession(
                                    token,
                                    actualUsername
                            );

                            ((MainActivity) requireActivity())
                                    .showTimeline();

                        } catch (Exception error) {
                            Toast.makeText(
                                    requireContext(),
                                    "Invalid server response",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    },
                    error -> {
                        if (!isAdded()) return;
                        login.setEnabled(true);

                        String message = "Connection failed";

                        if (error.networkResponse != null) {
                            int status =
                                    error.networkResponse.statusCode;

                            if (status == 401) {
                                message = "Incorrect username or password";
                            } else {
                                message = "Login failed: HTTP " + status;
                            }
                        }

                        Toast.makeText(
                                requireContext(),
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
            );
        });

        register.setOnClickListener(v ->
                ((MainActivity) requireActivity())
                        .showRegister()
        );
    }

    private EditText passwordInput(String hint) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return input;
    }

    private void showForgotPasswordDialog() {
        EditText emailInput = new EditText(requireContext());
        emailInput.setHint("Registered email");
        emailInput.setSingleLine(true);
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setPadding(48, 24, 48, 24);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Forgot password")
                .setMessage("Enter the email used to register your account.")
                .setView(emailInput)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send code", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String email = emailInput.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        emailInput.setError("Enter a valid email address");
                        return;
                    }
                    JSONObject body = new JSONObject();
                    try {
                        body.put("email", email);
                    } catch (Exception error) {
                        return;
                    }
                    Button sendButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    sendButton.setEnabled(false);
                    JsonObjectRequest request = new JsonObjectRequest(
                            Request.Method.POST, BASE_URL + "/api/auth/forgot-password", body,
                            response -> {
                                if (!isAdded()) return;
                                dialog.dismiss();
                                Toast.makeText(requireContext(),
                                        "If the email is registered, a code was sent. Check your inbox and spam.",
                                        Toast.LENGTH_LONG).show();
                                showResetPasswordDialog(email);
                            },
                            error -> {
                                if (!isAdded()) return;
                                sendButton.setEnabled(true);
                                showRequestError(error, "Could not send code");
                            }
                    );
                    Volley.newRequestQueue(requireContext().getApplicationContext()).add(request);
                }));
        dialog.show();
    }

    private void showResetPasswordDialog(String email) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 8);

        EditText codeInput = new EditText(requireContext());
        codeInput.setHint("6-digit email code");
        codeInput.setSingleLine(true);
        codeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        codeInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
        EditText newPasswordInput = passwordInput("New password (at least 8 characters)");
        EditText confirmInput = passwordInput("Confirm new password");
        layout.addView(codeInput);
        layout.addView(newPasswordInput);
        layout.addView(confirmInput);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Reset password")
                .setMessage("Enter the code sent to " + email)
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String code = codeInput.getText().toString().trim();
                    String newPassword = newPasswordInput.getText().toString();
                    if (!code.matches("[0-9]{6}")) {
                        codeInput.setError("Enter the 6-digit code");
                        return;
                    }
                    if (newPassword.length() < 8 ||
                            newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
                        newPasswordInput.setError("Password must be 8–72 bytes");
                        return;
                    }
                    if (!newPassword.equals(confirmInput.getText().toString())) {
                        confirmInput.setError("Passwords do not match");
                        return;
                    }
                    JSONObject body = new JSONObject();
                    try {
                        body.put("email", email);
                        body.put("code", code);
                        body.put("newPassword", newPassword);
                    } catch (Exception error) {
                        return;
                    }
                    Button resetButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    resetButton.setEnabled(false);
                    JsonObjectRequest request = new JsonObjectRequest(
                            Request.Method.POST, BASE_URL + "/api/auth/reset-password", body,
                            response -> {
                                if (!isAdded()) return;
                                dialog.dismiss();
                                Toast.makeText(requireContext(),
                                        "Password reset. Log in with the new password.", Toast.LENGTH_LONG).show();
                            },
                            error -> {
                                if (!isAdded()) return;
                                resetButton.setEnabled(true);
                                showRequestError(error, "Invalid or expired code");
                            }
                    );
                    Volley.newRequestQueue(requireContext().getApplicationContext()).add(request);
                }));
        dialog.show();
    }

    private void showRequestError(com.android.volley.VolleyError error, String fallback) {
        int status = error.networkResponse == null ? 0 : error.networkResponse.statusCode;
        String message = status == 0 ? "Cannot reach server" :
                status == 503 ? "Email service is not configured on the server" :
                status == 500 ? "Server could not send/reset the code" : fallback;
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }
}
