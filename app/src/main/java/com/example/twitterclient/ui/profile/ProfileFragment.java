package com.example.twitterclient.ui.profile;

import android.app.AlertDialog;
import android.os.Bundle;
import android.net.Uri;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
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

import com.example.twitterclient.MainActivity;
import com.example.twitterclient.R;
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

public class ProfileFragment extends Fragment {
    private static final String SERVER_URL =
            "http://10.0.2.2:3000";

    private ImageView profileAvatar;
    private ImageView profileBanner;

    private String selectedImageKind = "avatar";

    private final ActivityResultLauncher<PickVisualMediaRequest> imagePicker =
            registerForActivityResult(
                    new ActivityResultContracts.PickVisualMedia(),
                    uri -> {
                        if (uri != null) {
                            uploadSelectedImage(uri, selectedImageKind);
                        }
                    }
            );

    private void pickProfileImage(String kind) {
        selectedImageKind = kind;

        imagePicker.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(
                                ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE
                        )
                        .build()
        );
    }


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
        profileAvatar = view.findViewById(R.id.imageProfileAvatar);
        profileBanner = view.findViewById(R.id.imageProfileBanner);

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
                        loadProfileImages(user);

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
        Button changeAvatar = new Button(requireContext());
        changeAvatar.setText("Change profile picture");
        changeAvatar.setOnClickListener(v -> pickProfileImage("avatar"));
        layout.addView(changeAvatar);
        Button changeBanner = new Button(requireContext());
        changeBanner.setText("Change banner");
        changeBanner.setOnClickListener(v -> pickProfileImage("banner"));
        layout.addView(changeBanner);

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
                        loadProfileImages(user);

                        View editBtn = requireView().findViewById(R.id.buttonEditProfile);
                        if (editBtn != null) {
                            editBtn.setVisibility(View.GONE);
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
    private void loadProfileImages(JSONObject user) {

        String avatarPath =
                user.optString("profileImageUrl", "");

        String bannerPath =
                user.optString("bannerImageUrl", "");

        // Load the avatar, if the account has one.
        if (!avatarPath.isEmpty() &&
                !avatarPath.equals("null")) {

            String fullAvatarUrl = avatarPath.startsWith("http")
                    ? avatarPath
                    : SERVER_URL + avatarPath;

            Glide.with(this)
                    .load(fullAvatarUrl)
                    .circleCrop()
                    .into(profileAvatar);

        } else {
            // No uploaded image: show the existing placeholder.
            profileAvatar.setImageResource(
                    android.R.drawable.ic_menu_myplaces
            );
        }

        // Load the banner, if the account has one.
        if (!bannerPath.isEmpty() &&
                !bannerPath.equals("null")) {

            String fullBannerUrl = bannerPath.startsWith("http")
                    ? bannerPath
                    : SERVER_URL + bannerPath;

            Glide.with(this)
                    .load(fullBannerUrl)
                    .centerCrop()
                    .into(profileBanner);

        } else {
            // Retain the gray placeholder when no banner exists.
            profileBanner.setImageDrawable(null);
        }
    }


    private void uploadSelectedImage(Uri imageUri, String kind) {

        if (!isAdded()) return;

        Toast.makeText(
                requireContext(),
                "Uploading image...",
                Toast.LENGTH_SHORT
        ).show();

        repository.uploadProfileImage(
                imageUri,
                kind,
                response -> {
                    if (!isAdded()) return;

                    Toast.makeText(
                            requireContext(),
                            "Image uploaded successfully",
                            Toast.LENGTH_SHORT
                    ).show();

                    // Reload the currently displayed profile.
                    String selectedUsername = null;

                    if (getArguments() != null) {
                        selectedUsername =
                                getArguments().getString(ARG_USERNAME);
                    }

                    if (selectedUsername == null) {
                        loadMyProfile();
                    } else {
                        loadProfileByUsername(selectedUsername);
                    }
                },
                error -> {
                    if (!isAdded()) return;

                    int status = error.networkResponse == null
                            ? 0
                            : error.networkResponse.statusCode;

                    String message;

                    if (status == 401) {
                        message = "Session expired. Please log in again.";
                    } else if (status == 400) {
                        message = "Invalid image or file too large";
                    } else if (error.getCause() != null) {
                        message = error.getCause().getMessage();
                    } else {
                        message = "Image upload failed";
                    }

                    Toast.makeText(
                            requireContext(),
                            message,
                            Toast.LENGTH_LONG
                    ).show();
                }
        );
    }

}
