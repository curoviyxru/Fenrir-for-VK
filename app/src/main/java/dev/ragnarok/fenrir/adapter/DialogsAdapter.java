package dev.ragnarok.fenrir.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso3.Transformation;

import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EventListener;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.ragnarok.fenrir.R;
import dev.ragnarok.fenrir.link.internal.OwnerLinkSpanFactory;
import dev.ragnarok.fenrir.model.ChatAction;
import dev.ragnarok.fenrir.model.Dialog;
import dev.ragnarok.fenrir.model.MessageType;
import dev.ragnarok.fenrir.model.User;
import dev.ragnarok.fenrir.model.UserPlatform;
import dev.ragnarok.fenrir.picasso.PicassoInstance;
import dev.ragnarok.fenrir.settings.CurrentTheme;
import dev.ragnarok.fenrir.settings.Settings;
import dev.ragnarok.fenrir.util.AppTextUtils;
import dev.ragnarok.fenrir.util.Objects;
import dev.ragnarok.fenrir.util.Utils;
import dev.ragnarok.fenrir.util.ViewUtils;
import dev.ragnarok.fenrir.view.OnlineView;

public class DialogsAdapter extends RecyclerView.Adapter<DialogsAdapter.DialogViewHolder> {

    public static final String PICASSO_TAG = "dialogs.adapter.tag";
    private static final Date DATE = new Date();
    private static final int DIV_DISABLE = 0;
    private static final int DIV_TODAY = 1;
    private static final int DIV_YESTERDAY = 2;
    private static final int DIV_THIS_WEEK = 3;
    private static final int DIV_OLD = 4;
    @SuppressLint("ConstantLocale")
    private static final SimpleDateFormat DF_TODAY = new SimpleDateFormat("HH:mm", Locale.getDefault());
    @SuppressLint("ConstantLocale")
    private static final SimpleDateFormat DF_OLD = new SimpleDateFormat("dd/MM", Locale.getDefault());
    private final Context mContext;
    private final Transformation mTransformation;
    private final ForegroundColorSpan mForegroundColorSpan;
    private final RecyclerView.AdapterDataObserver mDataObserver;
    private final Set<Integer> hidden;
    private List<Dialog> mDialogs;
    private long mStartOfToday;
    private ClickListener mClickListener;

    public DialogsAdapter(Context context, @NonNull List<Dialog> dialogs) {
        mContext = context;
        mDialogs = dialogs;
        mTransformation = CurrentTheme.createTransformationForAvatar(context);
        mForegroundColorSpan = new ForegroundColorSpan(CurrentTheme.getPrimaryTextColorCode(context));
        mDataObserver = new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                initStartOfTodayDate();
            }
        };
        hidden = new HashSet<>();

        registerAdapterDataObserver(mDataObserver);
        initStartOfTodayDate();
    }

    private void initStartOfTodayDate() {
        // А - Аптемезация
        mStartOfToday = Utils.startOfTodayMillis();
    }

    public void cleanup() {
        unregisterAdapterDataObserver(mDataObserver);
    }

    @NonNull
    @Override
    public DialogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new DialogViewHolder(LayoutInflater.from(mContext)
                .inflate(R.layout.item_dialog, parent, false));
    }

    public void updateHidden(Set<Integer> hidden) {
        this.hidden.clear();
        this.hidden.addAll(hidden);
    }

    @Override
    public void onBindViewHolder(@NotNull DialogViewHolder holder, int position) {
        Dialog dialog = mDialogs.get(position);

        boolean isHide = hidden.contains(dialog.getId()) && !Settings.get().security().getShowHiddenDialogs();

        if (isHide)
            holder.mDialogContentRoot.setVisibility(View.INVISIBLE);
        else
            holder.mDialogContentRoot.setVisibility(View.VISIBLE);

        Dialog previous = position == 0 ? null : mDialogs.get(position - 1);

        holder.mDialogTitle.setText(dialog.getDisplayTitle(mContext));

        SpannableStringBuilder lastMessage;

        Spannable query = OwnerLinkSpanFactory.withSpans(dialog.getLastMessageBody() != null ? dialog.getLastMessageBody() : "", true, false, null);
        if (query == null) {
            lastMessage = dialog.getLastMessageBody() != null ?
                    SpannableStringBuilder.valueOf(dialog.getLastMessageBody()) : new SpannableStringBuilder();
        } else {
            lastMessage = new SpannableStringBuilder();
            lastMessage.append(query);
        }

        @MessageType int attachment_message = MessageType.NO;
        if (dialog.getMessage() != null) {
            attachment_message = dialog.getMessage().getMessageTypeByAttachments();
        }

        if (attachment_message != MessageType.NO) {
            String type;
            switch (attachment_message) {
                case MessageType.AUDIO:
                    type = mContext.getString(R.string.audio_message);
                    break;
                case MessageType.CALL:
                    type = mContext.getString(R.string.call_message);
                    break;
                case MessageType.DOC:
                    type = mContext.getString(R.string.doc_message);
                    break;
                case MessageType.GIFT:
                    type = mContext.getString(R.string.gift_message);
                    break;
                case MessageType.GRAFFITY:
                    type = mContext.getString(R.string.graffity_message);
                    break;
                case MessageType.PHOTO:
                    type = mContext.getString(R.string.photo_message);
                    break;
                case MessageType.STICKER:
                    type = mContext.getString(R.string.sticker_message);
                    break;
                case MessageType.VIDEO:
                    type = mContext.getString(R.string.video_message);
                    break;
                case MessageType.VOICE:
                    type = mContext.getString(R.string.voice_message);
                    break;
                case MessageType.WALL:
                    type = mContext.getString(R.string.wall_message);
                    break;
                default:
                    type = mContext.getString(R.string.attachments);
                    break;
            }
            SpannableStringBuilder spannable = SpannableStringBuilder.valueOf(type);
            spannable.setSpan(new ForegroundColorSpan(CurrentTheme.getColorPrimary(mContext)), 0, spannable.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            lastMessage = TextUtils.isEmpty(lastMessage) ?
                    spannable : lastMessage.append(" ").append(spannable);
        }

        if (dialog.hasForwardMessages()) {
            SpannableStringBuilder spannable = SpannableStringBuilder.valueOf(mContext.getString(R.string.forward_messages));
            spannable.setSpan(new ForegroundColorSpan(CurrentTheme.getColorPrimary(mContext)), 0, spannable.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

            lastMessage = TextUtils.isEmpty(lastMessage) ?
                    spannable : lastMessage.append(" ").append(spannable);
        }

        Integer lastMessageAction = dialog.getLastMessageAction();
        if (Objects.nonNull(lastMessageAction) && lastMessageAction != ChatAction.NO_ACTION) {
            SpannableStringBuilder spannable = SpannableStringBuilder.valueOf(mContext.getString(R.string.service_message));
            spannable.setSpan(new ForegroundColorSpan(CurrentTheme.getColorPrimary(mContext)), 0, spannable.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

            lastMessage = spannable;
        }

        if (dialog.isChat()) {
            SpannableStringBuilder spannable = SpannableStringBuilder.valueOf(dialog.isLastMessageOut() ? mContext.getString(R.string.dialog_me) : dialog.getSenderShortName(mContext));
            spannable.setSpan(mForegroundColorSpan, 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            lastMessage = spannable.append(": ").append(lastMessage);
        }

        holder.mDialogMessage.setText(lastMessage);


        boolean lastMessageRead = dialog.isLastMessageRead();
        int titleTextStyle = getTextStyle(dialog.isLastMessageOut(), lastMessageRead);
        holder.mDialogTitle.setTypeface(null, titleTextStyle);

        boolean online = false;
        boolean onlineMobile = false;

        @UserPlatform
        int platform = UserPlatform.UNKNOWN;
        int app = 0;

        if (dialog.getInterlocutor() instanceof User) {
            User interlocutor = (User) dialog.getInterlocutor();
            holder.mDialogTitle.setTextColor(Utils.getVerifiedColor(mContext, interlocutor.isVerified()));
            online = interlocutor.isOnline();
            onlineMobile = interlocutor.isOnlineMobile();
            platform = interlocutor.getPlatform();
            app = interlocutor.getOnlineApp();
            if (!dialog.isChat()) {
                holder.ivVerified.setVisibility(interlocutor.isVerified() ? View.VISIBLE : View.GONE);
                holder.blacklisted.setVisibility(interlocutor.getBlacklisted() ? View.VISIBLE : View.GONE);
            } else {
                holder.blacklisted.setVisibility(View.GONE);
                holder.ivVerified.setVisibility(View.GONE);
            }
        } else {
            holder.ivVerified.setVisibility(View.GONE);
            holder.blacklisted.setVisibility(View.GONE);
            holder.mDialogTitle.setTextColor(Utils.getVerifiedColor(mContext, false));
        }

        Integer iconRes = ViewUtils.getOnlineIcon(online, onlineMobile, platform, app);
        holder.ivOnline.setIcon(iconRes != null ? iconRes : 0);

        holder.ivDialogType.setImageResource(dialog.isGroupChannel() ? R.drawable.channel : R.drawable.person_multiple);
        holder.ivDialogType.setVisibility(dialog.isChat() ? View.VISIBLE : View.GONE);
        holder.ivUnreadTicks.setVisibility(dialog.isLastMessageOut() ? View.VISIBLE : View.GONE);
        holder.ivUnreadTicks.setImageResource(lastMessageRead ? R.drawable.check_all : R.drawable.check);

        holder.ivOnline.setVisibility(online && !dialog.isChat() ? View.VISIBLE : View.GONE);

        boolean counterVisible = dialog.getUnreadCount() > 0;
        holder.tvUnreadCount.setText(AppTextUtils.getCounterWithK(dialog.getUnreadCount()));
        holder.tvUnreadCount.setVisibility(counterVisible ? View.VISIBLE : View.INVISIBLE);

        long lastMessageJavaTime = dialog.getLastMessageDate() * 1000;
        int headerStatus = getDivided(lastMessageJavaTime, previous == null ? null : previous.getLastMessageDate() * 1000);

        switch (headerStatus) {
            case DIV_DISABLE:
            case DIV_TODAY:
                holder.mHeaderTitle.setVisibility(View.GONE);
                //holder.mHeaderTitle.setVisibility(View.VISIBLE);
                //holder.mHeaderTitle.setText(R.string.dialog_day_today);
                break;
            case DIV_OLD:
                holder.mHeaderTitle.setVisibility(View.VISIBLE);
                holder.mHeaderTitle.setText(R.string.dialog_day_older);
                break;
            case DIV_YESTERDAY:
                holder.mHeaderTitle.setVisibility(View.VISIBLE);
                holder.mHeaderTitle.setText(R.string.dialog_day_yesterday);
                break;
            case DIV_THIS_WEEK:
                holder.mHeaderTitle.setVisibility(View.VISIBLE);
                holder.mHeaderTitle.setText(R.string.dialog_day_ten_days);
                break;
        }

        DATE.setTime(lastMessageJavaTime);
        if (lastMessageJavaTime < mStartOfToday) {
            holder.tvDate.setTextColor(CurrentTheme.getPrimaryTextColorCode(mContext));
            if (getStatus(lastMessageJavaTime) == DIV_YESTERDAY)
                holder.tvDate.setText(DF_TODAY.format(DATE));
            else
                holder.tvDate.setText(DF_OLD.format(DATE));
        } else {
            holder.tvDate.setText(DF_TODAY.format(DATE));
            holder.tvDate.setTextColor(CurrentTheme.getColorPrimary(mContext));
        }

        if (isHide) {
            PicassoInstance.with().cancelRequest(holder.ivAvatar);
            holder.EmptyAvatar.setVisibility(View.VISIBLE);
            holder.EmptyAvatar.setText("@");
            holder.ivAvatar.setImageBitmap(mTransformation.transform(Utils.createGradientChatImage(200, 200, dialog.getId())).getBitmap());
        } else {
            if (dialog.getImageUrl() != null) {
                holder.EmptyAvatar.setVisibility(View.INVISIBLE);
                ViewUtils.displayAvatar(holder.ivAvatar, mTransformation, dialog.getImageUrl(), PICASSO_TAG);
            } else {
                PicassoInstance.with().cancelRequest(holder.ivAvatar);
                holder.EmptyAvatar.setVisibility(View.VISIBLE);
                String name = dialog.getTitle();
                if (name.length() > 2)
                    name = name.substring(0, 2);
                name = name.trim();
                holder.EmptyAvatar.setText(name);
                holder.ivAvatar.setImageBitmap(mTransformation.transform(Utils.createGradientChatImage(200, 200, dialog.getId())).getBitmap());
            }
        }

        holder.mContentRoot.setOnClickListener(v -> {
            if (mClickListener != null && !isHide) {
                mClickListener.onDialogClick(dialog, position);
            }
        });
        holder.ivAvatar.setOnClickListener(view -> {
            if (Objects.nonNull(mClickListener) && !isHide) {
                mClickListener.onAvatarClick(dialog, position);
            }
        });

        holder.mContentRoot.setOnLongClickListener(v -> mClickListener != null && mClickListener.onDialogLongClick(dialog));
    }

    private int getTextStyle(boolean out, boolean read) {
        return read || out ? Typeface.NORMAL : Typeface.BOLD;
    }

    private int getDivided(long messageDateJavaTime, Long previousMessageDateJavaTime) {
        int stCurrent = getStatus(messageDateJavaTime);
        if (previousMessageDateJavaTime == null) {
            return stCurrent;
        } else {
            int stPrevious = getStatus(previousMessageDateJavaTime);
            if (stCurrent == stPrevious) {
                return DIV_DISABLE;
            } else {
                return stCurrent;
            }
        }
    }

    private int getStatus(long time) {
        if (time >= mStartOfToday) {
            return DIV_TODAY;
        }

        if (time >= mStartOfToday - 86400000) {
            return DIV_YESTERDAY;
        }

        if (time >= mStartOfToday - 864000000) {
            return DIV_THIS_WEEK;
        }

        return DIV_OLD;
    }

    public DialogsAdapter setClickListener(ClickListener clickListener) {
        mClickListener = clickListener;
        return this;
    }

    public void setData(List<Dialog> data) {
        mDialogs = data;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mDialogs.size();
    }

    public interface ClickListener extends EventListener {
        void onDialogClick(Dialog dialog, int offset);

        boolean onDialogLongClick(Dialog dialog);

        void onAvatarClick(Dialog dialog, int offset);
    }

    static class DialogViewHolder extends RecyclerView.ViewHolder {

        final View mContentRoot;
        final TextView mDialogTitle;
        final TextView mDialogMessage;
        final ImageView ivDialogType;
        final ImageView ivAvatar;
        final ImageView ivVerified;
        final ImageView blacklisted;
        final TextView tvUnreadCount;
        final ImageView ivUnreadTicks;
        final OnlineView ivOnline;
        final TextView tvDate;
        final View mDialogContentRoot;
        final TextView mHeaderTitle;
        final TextView EmptyAvatar;

        DialogViewHolder(View view) {
            super(view);
            mContentRoot = view.findViewById(R.id.content_root);
            mDialogTitle = view.findViewById(R.id.dialog_title);
            mDialogMessage = view.findViewById(R.id.dialog_message);
            ivDialogType = view.findViewById(R.id.dialog_type);
            ivUnreadTicks = view.findViewById(R.id.unread_ticks);
            ivAvatar = view.findViewById(R.id.item_chat_avatar);
            tvUnreadCount = view.findViewById(R.id.item_chat_unread_count);
            ivOnline = view.findViewById(R.id.item_chat_online);
            tvDate = view.findViewById(R.id.item_chat_date);
            mHeaderTitle = view.findViewById(R.id.header_title);
            EmptyAvatar = view.findViewById(R.id.empty_avatar_text);
            mDialogContentRoot = view.findViewById(R.id.dialog_content);
            blacklisted = itemView.findViewById(R.id.item_blacklisted);
            ivVerified = itemView.findViewById(R.id.item_verified);
        }
    }
}