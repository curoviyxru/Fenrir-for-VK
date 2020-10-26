package dev.ragnarok.fenrir.adapter;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.squareup.picasso3.Transformation;
import com.umerov.rlottie.RLottieImageView;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

import dev.ragnarok.fenrir.Constants;
import dev.ragnarok.fenrir.R;
import dev.ragnarok.fenrir.modalbottomsheetdialogfragment.ModalBottomSheetDialogFragment;
import dev.ragnarok.fenrir.modalbottomsheetdialogfragment.OptionRequest;
import dev.ragnarok.fenrir.model.Audio;
import dev.ragnarok.fenrir.model.menu.AudioItem;
import dev.ragnarok.fenrir.picasso.PicassoInstance;
import dev.ragnarok.fenrir.picasso.transforms.PolyTransformation;
import dev.ragnarok.fenrir.picasso.transforms.RoundTransformation;
import dev.ragnarok.fenrir.place.PlaceFactory;
import dev.ragnarok.fenrir.player.util.MusicUtils;
import dev.ragnarok.fenrir.settings.CurrentTheme;
import dev.ragnarok.fenrir.settings.Settings;
import dev.ragnarok.fenrir.util.AppTextUtils;
import dev.ragnarok.fenrir.util.CustomToast;
import dev.ragnarok.fenrir.util.RxUtils;
import dev.ragnarok.fenrir.util.Utils;
import dev.ragnarok.fenrir.view.WeakViewAnimatorAdapter;
import io.reactivex.rxjava3.disposables.Disposable;

import static dev.ragnarok.fenrir.player.util.MusicUtils.observeServiceBinding;

public class AudioLocalRecyclerAdapter extends RecyclerView.Adapter<AudioLocalRecyclerAdapter.AudioHolder> {

    private final Context mContext;
    private ClickListener mClickListener;
    private Disposable mPlayerDisposable = Disposable.disposed();
    private List<Audio> data;
    private Audio currAudio;

    public AudioLocalRecyclerAdapter(Context context, List<Audio> data) {
        this.data = data;
        mContext = context;
        currAudio = MusicUtils.getCurrentAudio();
    }

    public void setItems(List<Audio> data) {
        this.data = data;
        notifyDataSetChanged();
    }

    @DrawableRes
    private int getAudioCoverSimple() {
        return Settings.get().main().isAudio_round_icon() ? R.drawable.audio_button : R.drawable.audio_button_material;
    }

    private Transformation TransformCover() {
        return Settings.get().main().isAudio_round_icon() ? new RoundTransformation() : new PolyTransformation();
    }

    private void updateAudioStatus(AudioHolder holder, Audio audio) {
        if (!audio.equals(currAudio)) {
            holder.visual.setImageResource(Utils.isEmpty(audio.getUrl()) ? R.drawable.audio_died : R.drawable.song);
            holder.play_cover.clearColorFilter();
            return;
        }
        switch (MusicUtils.PlayerStatus()) {
            case 1:
                Utils.doWavesLottie(holder.visual, true);
                holder.play_cover.setColorFilter(Color.parseColor("#44000000"));
                break;
            case 2:
                Utils.doWavesLottie(holder.visual, false);
                holder.play_cover.setColorFilter(Color.parseColor("#44000000"));
                break;

        }
    }

    @NotNull
    @Override
    public AudioHolder onCreateViewHolder(@NotNull ViewGroup parent, int viewType) {
        return new AudioHolder(LayoutInflater.from(mContext).inflate(R.layout.item_local_audio, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull AudioHolder holder, int position) {
        Audio audio = data.get(position);

        holder.cancelSelectionAnimation();
        if (audio.isAnimationNow()) {
            holder.startSelectionAnimation();
            audio.setAnimationNow(false);
        }

        holder.artist.setText(audio.getArtist());
        holder.title.setText(audio.getTitle());

        if (audio.getDuration() <= 0)
            holder.time.setVisibility(View.INVISIBLE);
        else {
            holder.time.setVisibility(View.VISIBLE);
            holder.time.setText(AppTextUtils.getDurationString(audio.getDuration()));
        }

        updateAudioStatus(holder, audio);

        if (Settings.get().other().isShow_audio_cover()) {
            if (!Utils.isEmpty(audio.getThumb_image_little())) {
                PicassoInstance.with()
                        .load(audio.getThumb_image_little())
                        .placeholder(Objects.requireNonNull(ResourcesCompat.getDrawable(mContext.getResources(), getAudioCoverSimple(), mContext.getTheme())))
                        .transform(TransformCover())
                        .tag(Constants.PICASSO_TAG)
                        .into(holder.play_cover);
            } else {
                PicassoInstance.with().cancelRequest(holder.play_cover);
                holder.play_cover.setImageResource(getAudioCoverSimple());
            }
        } else {
            PicassoInstance.with().cancelRequest(holder.play_cover);
            holder.play_cover.setImageResource(getAudioCoverSimple());
        }

        holder.play.setOnClickListener(v -> {
            if (MusicUtils.isNowPlayingOrPreparingOrPaused(audio)) {
                if (!Settings.get().other().isUse_stop_audio()) {
                    MusicUtils.playOrPause();
                } else {
                    MusicUtils.stop();
                }
            } else {
                if (mClickListener != null) {
                    mClickListener.onClick(position, audio);
                }
            }
        });
        holder.Track.setOnClickListener(view -> {
            holder.cancelSelectionAnimation();
            holder.startSomeAnimation();

            ModalBottomSheetDialogFragment.Builder menus = new ModalBottomSheetDialogFragment.Builder();

            menus.add(new OptionRequest(AudioItem.save_item_audio, mContext.getString(R.string.upload), R.drawable.web));
            menus.add(new OptionRequest(AudioItem.play_item_audio, mContext.getString(R.string.play), R.drawable.play));
            menus.add(new OptionRequest(AudioItem.add_item_audio, mContext.getString(R.string.delete), R.drawable.ic_outline_delete));


            menus.header(Utils.firstNonEmptyString(audio.getArtist(), " ") + " - " + audio.getTitle(), R.drawable.song, audio.getThumb_image_little());
            menus.columns(2);
            menus.show(((FragmentActivity) mContext).getSupportFragmentManager(), "audio_options", option -> {
                switch (option.getId()) {
                    case AudioItem.save_item_audio:
                        if (mClickListener != null) {
                            mClickListener.onUpload(position, audio);
                        }
                        break;
                    case AudioItem.play_item_audio:
                        if (mClickListener != null) {
                            mClickListener.onClick(position, audio);
                            if (Settings.get().other().isShow_mini_player())
                                PlaceFactory.getPlayerPlace(Settings.get().accounts().getCurrent()).tryOpenWith(mContext);
                        }
                        break;
                    case AudioItem.add_item_audio:
                        try {
                            if (mContext.getContentResolver().delete(Uri.parse(audio.getUrl()), null, null) == 1) {
                                Snackbar.make(view, R.string.success, BaseTransientBottomBar.LENGTH_LONG).show();
                                if (mClickListener != null) {
                                    mClickListener.onDelete(position);
                                }
                            }
                        } catch (Exception e) {
                            CustomToast.CreateCustomToast(mContext).showToastError(e.getLocalizedMessage());
                        }
                        break;
                    default:
                        break;
                }
            });
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @Override
    public void onAttachedToRecyclerView(@NotNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        mPlayerDisposable = observeServiceBinding()
                .compose(RxUtils.applyObservableIOToMainSchedulers())
                .subscribe(this::onServiceBindEvent);
    }

    @Override
    public void onDetachedFromRecyclerView(@NotNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        mPlayerDisposable.dispose();
    }

    private void onServiceBindEvent(@MusicUtils.PlayerStatus int status) {
        switch (status) {
            case MusicUtils.PlayerStatus.UPDATE_TRACK_INFO:
            case MusicUtils.PlayerStatus.SERVICE_KILLED:
            case MusicUtils.PlayerStatus.UPDATE_PLAY_PAUSE:
                updateAudio(currAudio);
                currAudio = MusicUtils.getCurrentAudio();
                updateAudio(currAudio);
                break;
            case MusicUtils.PlayerStatus.REPEATMODE_CHANGED:
            case MusicUtils.PlayerStatus.SHUFFLEMODE_CHANGED:
                break;
        }
    }

    private void updateAudio(Audio audio) {
        int pos = data.indexOf(audio);
        if (pos != -1) {
            notifyItemChanged(pos);
        }
    }

    public void setClickListener(ClickListener clickListener) {
        mClickListener = clickListener;
    }

    public interface ClickListener {
        void onClick(int position, Audio audio);

        void onDelete(int position);

        void onUpload(int position, Audio audio);
    }

    class AudioHolder extends RecyclerView.ViewHolder {

        final TextView artist;
        final TextView title;
        final View play;
        final ImageView play_cover;
        final View Track;
        final MaterialCardView selectionView;
        final Animator.AnimatorListener animationAdapter;
        ObjectAnimator animator;
        final RLottieImageView visual;
        final TextView time;

        AudioHolder(View itemView) {
            super(itemView);
            artist = itemView.findViewById(R.id.dialog_title);
            title = itemView.findViewById(R.id.dialog_message);
            time = itemView.findViewById(R.id.item_audio_time);
            play = itemView.findViewById(R.id.item_audio_play);
            play_cover = itemView.findViewById(R.id.item_audio_play_cover);
            Track = itemView.findViewById(R.id.track_option);
            selectionView = itemView.findViewById(R.id.item_audio_selection);
            visual = itemView.findViewById(R.id.item_audio_visual);
            animationAdapter = new WeakViewAnimatorAdapter<View>(selectionView) {
                @Override
                public void onAnimationEnd(View view) {
                    view.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationStart(View view) {
                    view.setVisibility(View.VISIBLE);
                }

                @Override
                protected void onAnimationCancel(View view) {
                    view.setVisibility(View.GONE);
                }
            };
        }

        void startSelectionAnimation() {
            selectionView.setCardBackgroundColor(CurrentTheme.getColorPrimary(mContext));
            selectionView.setAlpha(0.5f);

            animator = ObjectAnimator.ofFloat(selectionView, View.ALPHA, 0.0f);
            animator.setDuration(1500);
            animator.addListener(animationAdapter);
            animator.start();
        }

        void startSomeAnimation() {
            selectionView.setCardBackgroundColor(CurrentTheme.getColorSecondary(mContext));
            selectionView.setAlpha(0.5f);

            animator = ObjectAnimator.ofFloat(selectionView, View.ALPHA, 0.0f);
            animator.setDuration(500);
            animator.addListener(animationAdapter);
            animator.start();
        }

        void cancelSelectionAnimation() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }

            selectionView.setVisibility(View.INVISIBLE);
        }
    }
}
