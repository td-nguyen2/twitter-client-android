package com.example.twitterclient.ui.notifications;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.twitterclient.MainActivity;
import com.example.twitterclient.R;
import com.example.twitterclient.model.AppNotification;
import com.example.twitterclient.network.ApiService;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment {

    private NotificationAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyMessage;
    private ApiService apiService;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(
                R.layout.fragment_notifications,
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

        apiService = new ApiService(requireContext());

        adapter = new NotificationAdapter();
        adapter.setOnActorClickListener(username ->
                ((MainActivity) requireActivity()).showUserProfile(username)
        );
        adapter.setOnPostClickListener(postId ->
                ((MainActivity) requireActivity()).showPostDetail(postId)
        );

        recyclerView = view.findViewById(R.id.notifications_recycler_view);
        recyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext())
        );
        recyclerView.setAdapter(adapter);

        emptyMessage = view.findViewById(R.id.notifications_empty);

        // Back goes to the timeline fragment that opened this screen
        view.findViewById(R.id.notifications_back).setOnClickListener(v ->
                getParentFragmentManager().popBackStack()
        );

        loadNotifications();
    }

    private void loadNotifications() {
        apiService.getNotifications(
                response -> {
                    if (!isAdded() || adapter == null) {
                        return;
                    }

                    try {
                        JSONArray array =
                                response.getJSONArray("notifications");
                        List<AppNotification> items = new ArrayList<>();

                        for (int i = 0; i < array.length(); i++) {
                            items.add(
                                    new AppNotification(
                                            array.getJSONObject(i)
                                    )
                            );
                        }

                        adapter.replaceItems(items);

                        if (emptyMessage != null) {
                            emptyMessage.setVisibility(
                                    items.isEmpty()
                                            ? View.VISIBLE
                                            : View.GONE
                            );
                        }

                    } catch (JSONException error) {
                        showMessage("Invalid server response");
                    }
                },
                error -> {
                    int status = error.networkResponse != null
                            ? error.networkResponse.statusCode : -1;

                    if (status == 401 || status == 403) {
                        showMessage("Please log in again.");
                    } else {
                        showMessage("Could not load notifications.");
                    }
                }
        );
    }

    private void showMessage(String message) {
        if (isAdded()) {
            Toast.makeText(
                    requireContext(),
                    message,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    public void onDestroyView() {
        recyclerView = null;
        emptyMessage = null;
        super.onDestroyView();
    }
}
