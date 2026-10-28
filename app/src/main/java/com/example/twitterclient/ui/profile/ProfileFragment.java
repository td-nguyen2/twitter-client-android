package com.example.twitterclient.ui.profile;
import com.example.twitterclient.network.ApiService;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;

import android.net.Uri;
import android.widget.ImageView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import java.io.IOException;

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

    // Profile tabs: "posts", "replies", "reposts", "media"
    private String currentTab = "posts";
    private int profileUserId = -1;
    private String profileName = "";
    private String profileHandle = "";
    private TextView tabPosts, tabReplies, tabReposts, tabMedia;
    private View indicatorPosts, indicatorReplies, indicatorReposts, indicatorMedia;
    private Uri selectedAvatarUri;
    private Uri selectedBannerUri;

    private ImageView editAvatarPreview;
    private ImageView editBannerPreview;
    private ImageView profileAvatar;
    private ImageView profileBanner;
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
    private final ActivityResultLauncher<String> avatarPicker =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            selectedAvatarUri = uri;

                            if (editAvatarPreview != null) {
                                editAvatarPreview.setImageURI(uri);
                            }
                        }
                    }
            );

    private final ActivityResultLauncher<String> bannerPicker =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            selectedBannerUri = uri;

                            if (editBannerPreview != null) {
                                editBannerPreview.setImageURI(uri);
                            }
                        }
                    }
            );
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
        profileAvatar = view.findViewById(R.id.imageProfileAvatar);
        profileBanner = view.findViewById(R.id.imageProfileBanner);

        tabPosts = view.findViewById(R.id.tabPosts);
        tabReplies = view.findViewById(R.id.tabReplies);
        tabReposts = view.findViewById(R.id.tabReposts);
        tabMedia = view.findViewById(R.id.tabMedia);
        indicatorPosts = view.findViewById(R.id.indicatorPosts);
        indicatorReplies = view.findViewById(R.id.indicatorReplies);
        indicatorReposts = view.findViewById(R.id.indicatorReposts);
        indicatorMedia = view.findViewById(R.id.indicatorMedia);
        tabPosts.setOnClickListener(v -> selectTab("posts"));
        tabReplies.setOnClickListener(v -> selectTab("replies"));
        tabReposts.setOnClickListener(v -> selectTab("reposts"));
        tabMedia.setOnClickListener(v -> selectTab("media"));
        // Reuse the existing timeline TweetAdapter.
        RecyclerView recycler = view.findViewById(R.id.recyclerProfilePosts);

        tweetAdapter = new TweetAdapter();
        tweetAdapter.setOnAuthorClickListener(handle -> {
            ((MainActivity) requireActivity()).showUserProfile(handle);
        });
        tweetAdapter.setOnTweetClickListener(tweet ->
                ((MainActivity) requireActivity()).showPostDetail(tweet)
        );

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
                        String profileImageUrl = user.isNull("profileImageUrl")
                                ? null
                                : user.optString("profileImageUrl", null);

                        if (profileImageUrl != null
                                && !profileImageUrl.isEmpty()
                                && !profileImageUrl.equals("null")) {

                            String fullUrl = profileImageUrl.startsWith("http")
                                    ? profileImageUrl
                                    : "http://10.0.2.2:3000" + profileImageUrl;

                            Glide.with(this)
                                    .load(fullUrl)
                                    .transform(new CircleCrop())
                                    .placeholder(R.drawable.ui_default_avatar)
                                    .error(R.drawable.ui_default_avatar)
                                    .into(profileAvatar);

                        } else {
                            Glide.with(this).clear(profileAvatar);
                            profileAvatar.setImageResource(R.drawable.ui_default_avatar);
                        }

                        String bannerImageUrl = user.isNull("bannerImageUrl")
                                ? null
                                : user.optString("bannerImageUrl", null);

                        if (bannerImageUrl != null
                                && !bannerImageUrl.isEmpty()
                                && !bannerImageUrl.equals("null")) {

                            String fullBannerUrl = bannerImageUrl.startsWith("http")
                                    ? bannerImageUrl
                                    : "http://10.0.2.2:3000" + bannerImageUrl;

                            Glide.with(this)
                                    .load(fullBannerUrl)
                                    .centerCrop()
                                    .into(profileBanner);

                        } else {
                            Glide.with(this).clear(profileBanner);
                            profileBanner.setImageDrawable(null);
                        }

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

    // Switch the profile tab and reload the list under it
    private void selectTab(String tab) {
        if (tab.equals(currentTab)) return;
        currentTab = tab;
        updateTabStyles();
        tweetAdapter.replaceTweets(new ArrayList<>());
        emptyMessage.setVisibility(View.GONE);
        if (profileUserId > 0) {
            loadUserPosts(profileUserId, profileName, profileHandle);
        }
    }

    private void updateTabStyles() {
        styleTab(tabPosts, indicatorPosts, "posts".equals(currentTab));
        styleTab(tabReplies, indicatorReplies, "replies".equals(currentTab));
        styleTab(tabReposts, indicatorReposts, "reposts".equals(currentTab));
        styleTab(tabMedia, indicatorMedia, "media".equals(currentTab));
    }

    private void styleTab(TextView tab, View indicator, boolean selected) {
        if (tab == null || indicator == null) return;
        tab.setTextColor(android.graphics.Color.parseColor(selected ? "#0F1419" : "#536471"));
        tab.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        indicator.setBackgroundColor(selected
                ? android.graphics.Color.parseColor("#1D9BF0")
                : android.graphics.Color.TRANSPARENT);
    }

    private String emptyTextForTab() {
        switch (currentTab) {
            case "replies": return "No replies yet";
            case "reposts": return "No reposts yet";
            case "media": return "No photos or GIFs yet";
            default: return "No posts yet";
        }
    }

    private void loadUserPosts(int userId, String name, String handle) {
        profileUserId = userId;
        profileName = name;
        profileHandle = handle;
        final String requestedTab = currentTab;

        repository.getUserPosts(
                userId,
                requestedTab,
                response -> {
                    if (!isAdded()) return;
                    // Ignore a slow answer for a tab the user already left
                    if (!requestedTab.equals(currentTab)) return;

                    try {
                        JSONArray posts = response.getJSONArray("posts");
                        List<Tweet> tweets = new ArrayList<>();

                        for (int i = 0; i < posts.length(); i++) {
                            JSONObject post = posts.getJSONObject(i);
                            // The server already sends counts, flags and "repostedBy"
                            Tweet tweet = new Tweet(post);

                            // Older servers only send isRepost: use this profile's name
                            Object repostFlag = post.opt("isRepost");
                            boolean isRepost = Boolean.TRUE.equals(repostFlag)
                                    || (repostFlag instanceof Number
                                    && ((Number) repostFlag).intValue() != 0);

                            if (isRepost && tweet.getRepostedBy() == null) {
                                tweet.setRepostedBy(name);
                            }

                            tweets.add(tweet);
                        }

                        tweetAdapter.replaceTweets(tweets);

                        if ("posts".equals(requestedTab)) {
                            int count = tweets.size();
                            postCount.setText(count + (count == 1 ? " post" : " posts"));
                        }
                        emptyMessage.setText(emptyTextForTab());
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

        // Load our custom XML layout
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_profile, null);

        EditText nameInput = dialogView.findViewById(R.id.edit_profile_name);
        EditText bioInput = dialogView.findViewById(R.id.edit_profile_bio);

        View closeButton = dialogView.findViewById(R.id.edit_profile_close);
        Button saveButton = dialogView.findViewById(R.id.edit_profile_save);
        ApiService apiService = new ApiService(requireContext());
        editAvatarPreview = dialogView.findViewById(R.id.edit_avatar_preview);
        editBannerPreview = dialogView.findViewById(R.id.edit_banner_preview);

        View avatarButton = dialogView.findViewById(R.id.edit_avatar_button);
        View bannerButton = dialogView.findViewById(R.id.edit_banner_button);

        avatarButton.setOnClickListener(v -> avatarPicker.launch("image/*"));
        bannerButton.setOnClickListener(v -> bannerPicker.launch("image/*"));

        // Preserve the user's current name and bio
        nameInput.setText(displayName.getText());
        bioInput.setText(bio.getText());
        // Show the current saved avatar and banner in Edit Profile
        if (profileAvatar.getDrawable() != null) {
            editAvatarPreview.setImageDrawable(
                    profileAvatar.getDrawable().getConstantState() != null
                            ? profileAvatar.getDrawable()
                            .getConstantState().newDrawable().mutate()
                            : profileAvatar.getDrawable()
            );
        }

        if (profileBanner.getDrawable() != null) {
            editBannerPreview.setImageDrawable(
                    profileBanner.getDrawable().getConstantState() != null
                            ? profileBanner.getDrawable()
                            .getConstantState().newDrawable().mutate()
                            : profileBanner.getDrawable()
            );
        }

        // Create the custom dialog
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        // Close without saving
        closeButton.setOnClickListener(v -> dialog.dismiss());

        // Save the edited profile

        // Save name, bio, and optionally the selected avatar

        saveButton.setOnClickListener(v -> {
            String newName = nameInput.getText().toString().trim();
            String newBio = bioInput.getText().toString().trim();

            if (newName.isEmpty()) {
                nameInput.setError("Display name is required");
                return;
            }

            saveButton.setEnabled(false);

            // Remember which images were selected for this save
            Uri avatarToUpload = selectedAvatarUri;
            Uri bannerToUpload = selectedBannerUri;

            // Called only after every requested update succeeds
            Runnable finishSave = () -> {
                if (!isAdded() || !dialog.isShowing()) return;

                dialog.dismiss();
                loadMyProfile();

                Toast.makeText(
                        requireContext(),
                        "Profile updated",
                        Toast.LENGTH_SHORT
                ).show();
            };

            // Upload banner after the avatar stage finishes
            Runnable uploadBannerStep = () -> {
                if (!isAdded() || !dialog.isShowing()) return;

                if (bannerToUpload == null) {
                    finishSave.run();
                    return;
                }

                try {
                    apiService.uploadBanner(
                            bannerToUpload,

                            response -> {
                                if (!isAdded() || !dialog.isShowing()) return;

                                selectedBannerUri = null;
                                finishSave.run();
                            },

                            error -> {
                                if (!isAdded() || !dialog.isShowing()) return;

                                saveButton.setEnabled(true);

                                Toast.makeText(
                                        requireContext(),
                                        "Banner upload failed",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                    );

                } catch (IOException e) {
                    if (!isAdded() || !dialog.isShowing()) return;

                    saveButton.setEnabled(true);
                    Toast.makeText(
                            requireContext(),
                            e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                }
            };

            // First save name and bio
            repository.updateMyProfile(
                    newName,
                    newBio,

                    response -> {
                        if (!isAdded() || !dialog.isShowing()) return;

                        // Skip avatar upload if none was selected
                        if (avatarToUpload == null) {
                            uploadBannerStep.run();
                            return;
                        }

                        try {
                            apiService.uploadAvatar(
                                    avatarToUpload,

                                    avatarResponse -> {
                                        if (!isAdded() || !dialog.isShowing()) return;

                                        selectedAvatarUri = null;

                                        // Continue to banner upload
                                        uploadBannerStep.run();
                                    },

                                    error -> {
                                        if (!isAdded() || !dialog.isShowing()) return;

                                        saveButton.setEnabled(true);

                                        Toast.makeText(
                                                requireContext(),
                                                "Avatar upload failed",
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                            );

                        } catch (IOException e) {
                            if (!isAdded() || !dialog.isShowing()) return;

                            saveButton.setEnabled(true);

                            Toast.makeText(
                                    requireContext(),
                                    e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    },

                    error -> {
                        if (!isAdded() || !dialog.isShowing()) return;

                        saveButton.setEnabled(true);

                        Toast.makeText(
                                requireContext(),
                                "Unable to update name and bio",
                                Toast.LENGTH_LONG
                        ).show();
                    }
            );
        });

        dialog.setOnDismissListener(ignored -> {
            selectedAvatarUri = null;
            selectedBannerUri = null;
            editAvatarPreview = null;
            editBannerPreview = null;
        });
        dialog.show();

        // Make the popup fill the screen width
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );

            dialog.getWindow().setGravity(android.view.Gravity.CENTER);
            dialog.getWindow().setBackgroundDrawableResource(
                    android.R.color.transparent
            );
        }
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
                        // Load this user's avatar
                        String avatarUrl = user.optString("profileImageUrl", "");

                        if (!avatarUrl.isEmpty() && !avatarUrl.equals("null")) {
                            String fullUrl = avatarUrl.startsWith("http")
                                    ? avatarUrl
                                    : "http://10.0.2.2:3000" + avatarUrl;

                            Glide.with(this)
                                    .load(fullUrl)
                                    .transform(new CircleCrop())
                                    .placeholder(R.drawable.ui_default_avatar)
                                    .error(R.drawable.ui_default_avatar)
                                    .into(profileAvatar);
                        } else {
                            Glide.with(this).clear(profileAvatar);
                            profileAvatar.setImageResource(R.drawable.ui_default_avatar);
                        }

// Load this user's banner
                        String bannerUrl = user.optString("bannerImageUrl", "");

                        if (!bannerUrl.isEmpty() && !bannerUrl.equals("null")) {
                            String fullUrl = bannerUrl.startsWith("http")
                                    ? bannerUrl
                                    : "http://10.0.2.2:3000" + bannerUrl;

                            Glide.with(this)
                                    .load(fullUrl)
                                    .centerCrop()
                                    .into(profileBanner);
                        } else {
                            Glide.with(this).clear(profileBanner);
                            profileBanner.setImageDrawable(null);
                        }
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