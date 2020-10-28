package dev.ragnarok.fenrir.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.ragnarok.fenrir.Extra;
import dev.ragnarok.fenrir.R;
import dev.ragnarok.fenrir.activity.ActivityFeatures;
import dev.ragnarok.fenrir.activity.ActivityUtils;
import dev.ragnarok.fenrir.activity.SelectProfilesActivity;
import dev.ragnarok.fenrir.adapter.ChatMembersListAdapter;
import dev.ragnarok.fenrir.fragment.base.BaseMvpFragment;
import dev.ragnarok.fenrir.fragment.friends.FriendsTabsFragment;
import dev.ragnarok.fenrir.model.AppChatUser;
import dev.ragnarok.fenrir.model.Owner;
import dev.ragnarok.fenrir.model.SelectProfileCriteria;
import dev.ragnarok.fenrir.mvp.core.IPresenterFactory;
import dev.ragnarok.fenrir.mvp.presenter.ChatMembersPresenter;
import dev.ragnarok.fenrir.mvp.view.IChatMembersView;
import dev.ragnarok.fenrir.place.Place;
import dev.ragnarok.fenrir.place.PlaceFactory;
import dev.ragnarok.fenrir.util.AssertUtils;
import dev.ragnarok.fenrir.util.ViewUtils;

import static dev.ragnarok.fenrir.util.Objects.nonNull;

public class ChatUsersFragment extends BaseMvpFragment<ChatMembersPresenter, IChatMembersView>
        implements IChatMembersView, ChatMembersListAdapter.ActionListener {

    private static final int REQUEST_CODE_ADD_USER = 110;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private ChatMembersListAdapter mAdapter;

    public static Bundle buildArgs(int accountId, int chatId) {
        Bundle args = new Bundle();
        args.putInt(Extra.CHAT_ID, chatId);
        args.putInt(Extra.ACCOUNT_ID, accountId);
        return args;
    }

    public static ChatUsersFragment newInstance(Bundle args) {
        ChatUsersFragment fragment = new ChatUsersFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.fragment_chat_users, container, false);
        ((AppCompatActivity) requireActivity()).setSupportActionBar(root.findViewById(R.id.toolbar));

        RecyclerView recyclerView = root.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireActivity()));

        mAdapter = new ChatMembersListAdapter(requireActivity(), Collections.emptyList());
        mAdapter.setActionListener(this);
        recyclerView.setAdapter(mAdapter);

        mSwipeRefreshLayout = root.findViewById(R.id.refresh);
        mSwipeRefreshLayout.setOnRefreshListener(() -> getPresenter().fireRefresh());
        ViewUtils.setupSwipeRefreshLayoutWithCurrentTheme(requireActivity(), mSwipeRefreshLayout);

        FloatingActionButton fabAdd = root.findViewById(R.id.fragment_chat_users_add);
        fabAdd.setOnClickListener(v -> getPresenter().fireAddUserClick());
        return root;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ADD_USER && resultCode == Activity.RESULT_OK && data != null) {
            ArrayList<Owner> users = data.getParcelableArrayListExtra(Extra.OWNERS);
            AssertUtils.requireNonNull(users);

            postPrenseterReceive(presenter -> presenter.fireUserSelected(users));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ActionBar actionBar = ActivityUtils.supportToolbarFor(this);

        if (actionBar != null) {
            actionBar.setTitle(R.string.chat_users);
            actionBar.setSubtitle(null);
        }

        new ActivityFeatures.Builder()
                .begin()
                .setHideNavigationMenu(false)
                .setBarsColored(requireActivity(), true)
                .build()
                .apply(requireActivity());
    }

    @Override
    public void onRemoveClick(AppChatUser user) {
        new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.confirmation)
                .setMessage(getString(R.string.remove_chat_user_commit, user.getMember().getFullName()))
                .setPositiveButton(R.string.button_ok, (dialog, which) -> getPresenter().fireUserDeteleConfirmed(user))
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    @Override
    public void displayData(List<AppChatUser> users) {
        if (nonNull(mAdapter)) {
            mAdapter.setData(users);
        }
    }

    @Override
    public void notifyItemRemoved(int position) {
        if (nonNull(mAdapter)) {
            mAdapter.notifyItemRemoved(position);
        }
    }

    @Override
    public void notifyDataSetChanged() {
        if (nonNull(mAdapter)) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void notifyDataAdded(int position, int count) {
        if (nonNull(mAdapter)) {
            mAdapter.notifyItemRangeInserted(position, count);
        }
    }

    @Override
    public void openUserWall(int accountId, Owner user) {
        PlaceFactory.getOwnerWallPlace(accountId, user).tryOpenWith(requireActivity());
    }

    @Override
    public void displayRefreshing(boolean refreshing) {
        if (nonNull(mSwipeRefreshLayout)) {
            mSwipeRefreshLayout.setRefreshing(refreshing);
        }
    }

    @Override
    public void startSelectUsersActivity(int accountId) {
        Place place = PlaceFactory.getFriendsFollowersPlace(accountId, accountId, FriendsTabsFragment.TAB_ALL_FRIENDS, null);
        SelectProfileCriteria criteria = new SelectProfileCriteria().setOwnerType(SelectProfileCriteria.OwnerType.ONLY_FRIENDS);

        Intent intent = SelectProfilesActivity.createIntent(requireActivity(), place, criteria);

        startActivityForResult(intent, REQUEST_CODE_ADD_USER);
    }

    @NotNull
    @Override
    public IPresenterFactory<ChatMembersPresenter> getPresenterFactory(@Nullable Bundle saveInstanceState) {
        return () -> new ChatMembersPresenter(
                requireArguments().getInt(Extra.ACCOUNT_ID),
                requireArguments().getInt(Extra.CHAT_ID),
                saveInstanceState
        );
    }

    @Override
    public void onUserClick(AppChatUser user) {
        getPresenter().fireUserClick(user);
    }
}