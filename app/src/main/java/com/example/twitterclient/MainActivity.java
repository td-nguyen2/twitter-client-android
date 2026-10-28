package com.example.twitterclient;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.twitterclient.auth.SessionManager;
import com.example.twitterclient.model.Tweet;
import com.example.twitterclient.ui.auth.LoginFragment;
import com.example.twitterclient.ui.auth.RegisterFragment;
import com.example.twitterclient.ui.notifications.NotificationsFragment;
import com.example.twitterclient.ui.profile.ProfileFragment;
import com.example.twitterclient.ui.profile.SavedTweetsFragment;
import com.example.twitterclient.ui.timeline.PostDetailFragment;
import com.example.twitterclient.ui.timeline.TimelineFragment;

public class MainActivity extends AppCompatActivity {

    // Temporary test button (login screen only)
    private Button testButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            SessionManager session = new SessionManager(this);

            if (session.isLoggedIn()) {
                showTimeline();
            } else {
                showLogin();

                // TEMPORARY TEST: Open Post View without logging in
                testButton = new Button(this);
                testButton.setText("Test Post View");

                FrameLayout container =
                        findViewById(R.id.fragment_container);

                FrameLayout.LayoutParams params =
                        new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT
                        );

                params.gravity =
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                params.bottomMargin = 40;

                container.addView(testButton, params);

                testButton.setOnClickListener(v -> {
                    Tweet sampleTweet = new Tweet(
                            2L,
                            "Demo User",
                            "demo_user",
                            "This is a test post for Post View and Replies."
                    );

                    showPostDetail(sampleTweet);
                });
            }
        }
    }

    public void showProfile() {
        openFragment(new ProfileFragment());
    }

    public void showUserProfile(String username) {
        openFragment(ProfileFragment.forUsername(username));
    }

    public void showSavedTweets() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new SavedTweetsFragment())
                .addToBackStack(null)
                .commit();
    }

    public void showLogin() {
        openFragment(new LoginFragment());
    }

    public void showRegister() {
        openFragment(new RegisterFragment());
    }

    public void showTimeline() {
        // The temporary test button must not sit on top of the bottom bar
        removeTestButton();
        openFragment(new TimelineFragment());
    }

    // Open the notifications screen (back button returns to the timeline)
    public void showNotifications() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new NotificationsFragment())
                .addToBackStack(null)
                .commit();
    }

    private void removeTestButton() {
        if (testButton != null && testButton.getParent() instanceof ViewGroup) {
            ((ViewGroup) testButton.getParent()).removeView(testButton);
        }
        testButton = null;
    }

    // Open Post Detail screen
    public void showPostDetail(Tweet tweet) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.fragment_container,
                        PostDetailFragment.newInstance(tweet)
                )
                .addToBackStack(null)
                .commit();
    }

    // Open Post Detail screen from just a post id (e.g. from a notification)
    public void showPostDetail(long postId) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.fragment_container,
                        PostDetailFragment.newInstance(postId)
                )
                .addToBackStack(null)
                .commit();
    }

    // Helper method to switch fragments
    private void openFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
