package com.example.twitterclient.ui.profile;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.twitterclient.MainActivity;
import com.example.twitterclient.R;
import com.example.twitterclient.auth.SessionManager;
import com.example.twitterclient.model.Tweet;
import com.example.twitterclient.repository.ProfileRepository;
import com.example.twitterclient.ui.timeline.TweetAdapter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class ProfileFragment extends Fragment {
    private Button buttonFollow;
    private TextView textFollowCounts;
    private ProfileRepository repository;
    private TweetAdapter tweetAdapter;

    private TextView profileTitle;
    private TextView postCount;
    private TextView displayName;
    private TextView username;
    private TextView bio;
    private TextView joinedDate;
    private TextView emptyMessage;

    private static final String ARG_USERNAME = "profile_username";

    public static ProfileFragment forUsername(String username) {
        ProfileFragment fragment = new ProfileFragment();
        Bundle args = new Bundle();
        args.putString(ARG_USERNAME, username);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(
                R.layout.fragment_profile,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        buttonFollow = view.findViewById(R.id.buttonFollow);
        textFollowCounts = view.findViewById(R.id.textFollowCounts);
        repository = new ProfileRepository(requireContext());

        // Find the views defined in fragment_profile.xml
        profileTitle = view.findViewById(R.id.textProfileTitle);
        postCount = view.findViewById(R.id.textProfilePostCount);
        displayName = view.findViewById(R.id.textDisplayName);
        username = view.findViewById(R.id.textUsername);
        bio = view.findViewById(R.id.textBio);
        joinedDate = view.findViewById(R.id.textJoinedDate);
        emptyMessage = view.findViewById(R.id.textProfileEmpty);

        // Reuse the existing timeline TweetAdapter.
        RecyclerView recycler = view.findViewById(R.id.recyclerProfilePosts);

        tweetAdapter = new TweetAdapter();
        tweetAdapter.setOnAuthorClickListener(handle -> {
            ((MainActivity) requireActivity()).showUserProfile(handle);
        });

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(tweetAdapter);
        recycler.setNestedScrollingEnabled(false);

        // Back button returns to Home.
        view.findViewById(R.id.buttonBackProfile).setOnClickListener(v -> {
            ((MainActivity) requireActivity()).showTimeline();
        });

        // Edit profile button
        Button editButton = view.findViewById(R.id.buttonEditProfile);
        editButton.setOnClickListener(v -> showEditProfileDialog());

        Button changePasswordButton = view.findViewById(R.id.buttonChangePassword);
        changePasswordButton.setOnClickListener(v -> showChangePasswordDialog());

        String selectedUsername = null;
        if (getArguments() != null) {
            selectedUsername = getArguments().getString(ARG_USERNAME);
        }

        if (selectedUsername == null) {
            loadMyProfile();
        } else {
            loadProfileByUsername(selectedUsername);
        }
    }

    private void loadMyProfile() {
        repository.getMyProfile(
                response -> {
                    if (!isAdded()) return;

                    try {
                        JSONObject user = response.getJSONObject("user");
                        int userId = user.getInt("id");
                        String name = user.optString("displayName", "");
                        String handle = user.optString("username", "");
                        String description = user.optString("bio", "");
                        String createdAt = user.optString("createdAt", "");

                        profileTitle.setText(name);
                        displayName.setText(name);
                        username.setText("@" + handle);

                        if (!description.isEmpty() && !description.equals("null")) {
                            bio.setText(description);
                            bio.setVisibility(View.VISIBLE);
                        } else {
                            bio.setVisibility(View.GONE);
                        }

                        joinedDate.setText(formatJoinedDate(createdAt));

                        // Ẩn nút Follow ở trang cá nhân của chính mình
                        if (buttonFollow != null) {
                            buttonFollow.setVisibility(View.GONE);
                        }
                        if (textFollowCounts != null) {
                            // Tải thông tin follow của chính mình nếu cần hoặc ẩn
                        }

                        loadUserPosts(userId, name, handle);

                    } catch (Exception error) {
                        showError("Invalid profile response");
                    }
                },
                error -> {
                    if (!isAdded()) return;
                    if (error.networkResponse != null && error.networkResponse.statusCode == 401) {
                        showError("Session expired. Please log in again.");
                    } else {
                        showError("Cannot load profile from server");
                    }
                }
        );
    }

    private void loadUserPosts(int userId, String name, String handle) {
        repository.getUserPosts(
                userId,
                response -> {
                    if (!isAdded()) return;

                    try {
                        JSONArray posts = response.getJSONArray("posts");
                        List<Tweet> tweets = new ArrayList<>();

                        for (int i = 0; i < posts.length(); i++) {
                            JSONObject post = posts.getJSONObject(i);
                            String postUsername = post.optString("username", handle);
                            String content = post.optString("content", "");

                            // Tạo đối tượng JSON chuẩn để khởi tạo Tweet mới
                            JSONObject tweetJson = new JSONObject();
                            tweetJson.put("id", post.optInt("id", 0));
                            tweetJson.put("username", postUsername);
                            tweetJson.put("displayName", name);
                            tweetJson.put("text", content);
                            tweetJson.put("likeCount", post.optInt("likeCount", 0));
                            // Server sends 0/1, so pass the raw value through (Tweet reads both forms)
                            tweetJson.put("likedByMe", post.opt("likedByMe"));

                            tweets.add(new Tweet(tweetJson));
                        }

                        tweetAdapter.replaceTweets(tweets);

                        int count = tweets.size();
                        postCount.setText(count + (count == 1 ? " post" : " posts"));
                        emptyMessage.setVisibility(tweets.isEmpty() ? View.VISIBLE : View.GONE);

                    } catch (Exception error) {
                        showError("Invalid posts response");
                    }
                },
                error -> {
                    if (!isAdded()) return;
                    showError("Cannot load user posts");
                }
        );
    }

    private String formatJoinedDate(String dateString) {
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US);
            Date date = input.parse(dateString);
            if (date != null) {
                SimpleDateFormat output = new SimpleDateFormat("MMMM yyyy", Locale.US);
                return "Joined " + output.format(date);
            }
        } catch (Exception ignored) {}
        return "Joined date unavailable";
    }

    private void showError(String message) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    private void showEditProfileDialog() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 8);

        EditText nameInput = new EditText(requireContext());
        nameInput.setHint("Display name");
        nameInput.setSingleLine(true);
        nameInput.setText(displayName.getText());

        EditText bioInput = new EditText(requireContext());
        bioInput.setHint("Bio");
        bioInput.setMinLines(3);
        bioInput.setMaxLines(5);
        bioInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        bioInput.setText(bio.getText());

        layout.addView(nameInput);
        layout.addView(bioInput);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Edit profile")
                .setView(layout)
                .setNegativeButton("Cancel", (d, which) -> {})
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String newName = nameInput.getText().toString().trim();
                String newBio = bioInput.getText().toString().trim();

                if (newName.isEmpty()) {
                    nameInput.setError("Display name is required");
                    return;
                }

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

                repository.updateMyProfile(
                        newName,
                        newBio,
                        response -> {
                            if (!isAdded()) {
                                dialog.dismiss();
                                return;
                            }
                            dialog.dismiss();
                            loadMyProfile();
                            Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show();
                        },
                        error -> {
                            if (!isAdded()) {
                                dialog.dismiss();
                                return;
                            }
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            Toast.makeText(requireContext(), "Unable to update profile", Toast.LENGTH_LONG).show();
                        }
                );
            });
        });

        dialog.show();
    }

    private void loadProfileByUsername(String selectedUsername) {
        repository.getUserByUsername(
                selectedUsername,
                response -> {
                    if (!isAdded()) return;

                    try {
                        JSONObject user = response.getJSONObject("user");
                        int userId = user.getInt("id");
                        String name = user.optString("displayName", "");
                        String handle = user.optString("username", "");
                        String description = user.optString("bio", "");
                        String createdAt = user.optString("createdAt", "");

                        profileTitle.setText(name);
                        displayName.setText(name);
                        username.setText("@" + handle);

                        if (!description.isEmpty() && !description.equals("null")) {
                            bio.setText(description);
                            bio.setVisibility(View.VISIBLE);
                        } else {
                            bio.setVisibility(View.GONE);
                        }

                        joinedDate.setText(formatJoinedDate(createdAt));

                        View editBtn = requireView().findViewById(R.id.buttonEditProfile);
                        if (editBtn != null) {
                            editBtn.setVisibility(View.GONE);
                        }
                        View changePasswordBtn = requireView().findViewById(R.id.buttonChangePassword);
                        if (changePasswordBtn != null) {
                            changePasswordBtn.setVisibility(View.GONE);
                        }

                        loadUserPosts(userId, name, handle);
                        loadFollowData(userId);

                    } catch (Exception error) {
                        showError("Invalid profile response");
                    }
                },
                error -> {
                    if (!isAdded()) return;
                    if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                        showError("This user does not have an account");
                    } else {
                        showError("Cannot load this profile");
                    }
                }
        );
    }

    private void loadFollowData(int targetUserId) {
        repository.getFollowStatus(targetUserId, response -> {
            if (!isAdded()) return;
            int followers = response.optInt("followersCount", 0);
            int following = response.optInt("followingCount", 0);
            boolean isFollowing = response.optBoolean("isFollowing", false);

            if (textFollowCounts != null) {
                textFollowCounts.setText(following + " Following · " + followers + " Followers");
            }

            if (buttonFollow != null) {
                buttonFollow.setVisibility(View.VISIBLE);
                updateFollowButtonUI(isFollowing);

                buttonFollow.setOnClickListener(v -> {
                    repository.toggleFollow(targetUserId, res -> {
                        if (!isAdded()) return;
                        boolean newStatus = res.optBoolean("isFollowing", false);
                        int newFollowers = res.optInt("followersCount", followers);
                        int newFollowing = res.optInt("followingCount", following);

                        updateFollowButtonUI(newStatus);
                        if (textFollowCounts != null) {
                            textFollowCounts.setText(newFollowing + " Following · " + newFollowers + " Followers");
                        }
                    }, err -> Toast.makeText(requireContext(), "Không thể thực hiện", Toast.LENGTH_SHORT).show());
                });
            }
        }, error -> {});
    }

    private void updateFollowButtonUI(boolean isFollowing) {
        if (buttonFollow == null) return;
        if (isFollowing) {
            buttonFollow.setText("Following");
            buttonFollow.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            buttonFollow.setTextColor(android.graphics.Color.parseColor("#0F1419"));
        } else {
            buttonFollow.setText("Follow");
            buttonFollow.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#0F1419")));
            buttonFollow.setTextColor(android.graphics.Color.WHITE);
        }
    }

    private void showChangePasswordDialog() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 8);

        EditText currentInput = new EditText(requireContext());
        currentInput.setHint("Current password");
        currentInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        EditText newInput = new EditText(requireContext());
        newInput.setHint("New password");
        newInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        EditText confirmInput = new EditText(requireContext());
        confirmInput.setHint("Confirm new password");
        confirmInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        layout.addView(currentInput);
        layout.addView(newInput);
        layout.addView(confirmInput);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Change password")
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> {
                String currentPassword = currentInput.getText().toString();
                String newPassword = newInput.getText().toString();
                String confirmPassword = confirmInput.getText().toString();

                if (currentPassword.isEmpty()) {
                    currentInput.setError("Enter your current password");
                    return;
                }
                if (newPassword.length() < 8) {
                    newInput.setError("Use at least 8 characters");
                    return;
                }
                if (newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
                    newInput.setError("Password is too long (maximum 72 bytes)");
                    return;
                }
                if (newPassword.equals(currentPassword)) {
                    newInput.setError("Choose a different password");
                    return;
                }
                if (!newPassword.equals(confirmPassword)) {
                    confirmInput.setError("Passwords do not match");
                    return;
                }

                String token = new SessionManager(requireContext()).getToken();
                if (token == null || token.isEmpty()) {
                    showError("Please log in again");
                    return;
                }

                JSONObject body = new JSONObject();
                try {
                    body.put("currentPassword", currentPassword);
                    body.put("newPassword", newPassword);
                } catch (Exception error) {
                    showError("Cannot prepare request");
                    return;
                }

                saveButton.setEnabled(false);
                JsonObjectRequest request = new JsonObjectRequest(
                        Request.Method.POST,
                        "http://10.0.2.2:3000/api/auth/change-password",
                        body,
                        response -> {
                            if (!isAdded()) return;
                            dialog.dismiss();
                            showError("Password changed successfully");
                        },
                        error -> {
                            if (!isAdded()) return;
                            saveButton.setEnabled(true);
                            int status = error.networkResponse == null
                                    ? 0 : error.networkResponse.statusCode;
                            String message;
                            if (status == 401) {
                                message = "Current password is incorrect or session expired";
                            } else if (status == 400) {
                                message = "Check the new password";
                            } else if (status == 0) {
                                message = "Cannot reach server";
                            } else {
                                message = "Cannot change password (HTTP " + status + ")";
                            }
                            showError(message);
                        }
                ) {
                    @Override
                    public Map<String, String> getHeaders() {
                        Map<String, String> headers = new HashMap<>();
                        headers.put("Authorization", "Bearer " + token);
                        return headers;
                    }
                };

                Volley.newRequestQueue(requireContext().getApplicationContext()).add(request);
            });
        });
        dialog.show();
    }
}
