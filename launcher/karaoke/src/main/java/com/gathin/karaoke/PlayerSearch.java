package com.gathin.karaoke;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Search while a song plays, like a karaoke box: a low strip across the top of the player, results in one row; the
 * video goes on below it and its lyrics stay in sight. It opens on this month's trending songs; its tabs show the
 * favorites and the singers (typing there narrows the singers), and typing or saying a name searches. OK adds the
 * song to the list, long OK makes it the next one sung, Menu marks it a favorite. The list then plays on by itself
 * when the current song ends.
 */
final class PlayerSearch {
    /** What the row shows; RESULTS with shownSinger or shownGenre set is that singer's or genre's songs. */
    private static final int TRENDING = 0, FAVORITES = 1, SINGERS = 2, GENRES = 3, RESULTS = 4;

    private final Activity activity;
    private final View panel;
    private final EditText query;
    private final TextView status;
    private final RecyclerView list;
    private final LinearLayout keys;
    private final View tabTrending, tabFavorites, tabSingers, tabGenres;
    private final Adapter adapter = new Adapter();
    private final SingerAdapter singerAdapter = new SingerAdapter();
    private final GenreAdapter genreAdapter = new GenreAdapter();
    private final Voice voice;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Song> songs = new ArrayList<>();
    private final List<String> singers;
    private final List<String> shownSingers = new ArrayList<>();
    private SearchParser.Page page, trending;
    private String shownQuery, shownSinger;
    private KaraokeActivity.Genre shownGenre;
    private int mode = TRENDING;
    private boolean loading;
    private int searchId;

    PlayerSearch(Activity activity, ViewGroup root) {
        this.activity = activity;
        panel = LayoutInflater.from(activity).inflate(R.layout.player_search, root, false);
        root.addView(panel);
        query = panel.findViewById(R.id.ps_query);
        status = panel.findViewById(R.id.ps_status);
        list = panel.findViewById(R.id.ps_results);
        list.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false));
        list.setAdapter(adapter);
        list.setItemAnimator(null);
        voice = new Voice(activity, text -> {
            query.setText(text);
            search(text);
        });
        query.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                search(query.getText().toString());
                return true;
            }
            return false;
        });
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (mode == SINGERS) filterSingers();
            }
        });
        // The system keyboard would cover the lyrics: OK on the box opens the strip's own (a USB keyboard still types)
        query.setShowSoftInputOnFocus(false);
        query.setOnClickListener(v -> showKeys(true));
        keys = panel.findViewById(R.id.ps_keys);
        StripKeyboard.build(keys, query, () -> {
            showKeys(false);
            query.requestFocus();
            search(query.getText().toString());
        });
        panel.findViewById(R.id.ps_mic).setOnClickListener(v -> voice.start());
        singers = Arrays.asList(activity.getResources().getStringArray(R.array.singers));
        tabTrending = panel.findViewById(R.id.ps_tab_trending);
        tabFavorites = panel.findViewById(R.id.ps_tab_favorites);
        tabSingers = panel.findViewById(R.id.ps_tab_singers);
        tabTrending.setOnClickListener(v -> {
            query.setText("");
            loadTrending();
        });
        tabFavorites.setOnClickListener(v -> {
            query.setText("");
            showFavorites();
        });
        tabSingers.setOnClickListener(v -> {
            query.setText("");
            showSingers(null);
        });
        tabGenres = panel.findViewById(R.id.ps_tab_genres);
        tabGenres.setOnClickListener(v -> {
            query.setText("");
            showGenres(null);
        });
        setMode(TRENDING);
    }

    Voice voice() {
        return voice;
    }

    boolean isOpen() {
        return panel.getVisibility() == View.VISIBLE;
    }

    /** Height of the strip on screen, so the player's banner can sit just below it. */
    int height() {
        return panel.getHeight();
    }

    void open() {
        panel.setVisibility(View.VISIBLE);
        // Not the text box: focusing it can pop the keyboard up before anyone asked to type
        panel.findViewById(R.id.ps_mic).requestFocus();
        if (mode != SINGERS && mode != GENRES && songs.isEmpty()) {
            loadTrending();
        } else {
            updateStatus();
            list.post(() -> focusRow(0));
        }
    }

    /**
     * Back closes the keyboard first, and from a singer's or genre's songs goes to the singers or genres; false when
     * Back should close the strip.
     */
    boolean back() {
        if (keys.getVisibility() == View.VISIBLE) {
            showKeys(false);
            query.requestFocus();
            return true;
        }
        if (mode != RESULTS) return false;
        if (shownSinger != null) {
            showSingers(shownSinger);
            return true;
        }
        if (shownGenre != null) {
            showGenres(shownGenre);
            return true;
        }
        return false;
    }

    void close() {
        voice.stop();
        hideKeyboard();
        showKeys(false);
        panel.setVisibility(View.GONE);
    }

    /** The keyboard takes the row's place, so the strip stays low; the singers stay, as typing narrows them. */
    private void showKeys(boolean show) {
        keys.setVisibility(show ? View.VISIBLE : View.GONE);
        list.setVisibility(show && mode != SINGERS ? View.GONE : View.VISIBLE);
        if (show) ((ViewGroup) keys.getChildAt(0)).getChildAt(0).requestFocus();
    }

    void destroy() {
        voice.stop();
        worker.shutdownNow();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(query.getWindowToken(), 0);
    }

    private void setMode(int m) {
        mode = m;
        tabTrending.setSelected(m == TRENDING);
        tabFavorites.setSelected(m == FAVORITES);
        tabSingers.setSelected(m == SINGERS || shownSinger != null);
        tabGenres.setSelected(m == GENRES || shownGenre != null);
    }

    /** Back to the song row. The focus leaves the tiles first, or it would fall on the text box. */
    private void useSongRow() {
        if (list.getAdapter() == adapter) return;
        if (list.hasFocus()) (shownGenre != null ? tabGenres : tabSingers).requestFocus();
        songs.clear();
        list.setAdapter(adapter);
    }

    /** Drops a search still on its way, or its answer would replace the list being shown. */
    private void dropSearch() {
        searchId++;
        loading = false;
        showKeys(false);
    }

    private void loadTrending() {
        shownSinger = null;
        shownGenre = null;
        if (trending != null) {
            dropSearch();
            useSongRow();
            setMode(TRENDING);
            show(trending, null, false);
            return;
        }
        run("karaoke", YouTubeSearch.VIDEOS_THIS_MONTH, null, true);
    }

    private void showFavorites() {
        dropSearch();
        shownSinger = null;
        shownGenre = null;
        shownQuery = null;
        page = null;
        useSongRow();
        setMode(FAVORITES);
        songs.clear();
        songs.addAll(Library.get(activity).favorites());
        adapter.notifyDataSetChanged();
        list.scrollToPosition(0);
        updateStatus();
        if (songs.isEmpty()) tabFavorites.requestFocus();
        else list.post(() -> focusRow(0));
    }

    /** The singers matching the text box, the focus on focusName (null: the first). */
    private void showSingers(String focusName) {
        dropSearch();
        shownSinger = null;
        shownGenre = null;
        shownQuery = null;
        page = null;
        setMode(SINGERS);
        if (list.getAdapter() != singerAdapter) list.setAdapter(singerAdapter);
        filterSingers();
        int at = Math.max(shownSingers.indexOf(focusName), 0);
        list.scrollToPosition(at);
        list.post(() -> focusRow(at));
    }

    private void filterSingers() {
        String typed = query.getText().toString();
        shownSingers.clear();
        for (String s : singers) if (SearchParser.matchesName(s, typed)) shownSingers.add(s);
        singerAdapter.notifyDataSetChanged();
        updateStatus();
    }

    private void showSinger(String name) {
        shownSinger = name;
        shownGenre = null;
        run(SearchParser.query(name), YouTubeSearch.VIDEOS_ONLY, name, false);
    }

    /** The genres, the focus on focus (null: the first). */
    private void showGenres(KaraokeActivity.Genre focus) {
        dropSearch();
        shownSinger = null;
        shownGenre = null;
        shownQuery = null;
        page = null;
        setMode(GENRES);
        if (list.getAdapter() != genreAdapter) list.setAdapter(genreAdapter);
        updateStatus();
        int at = Math.max(Arrays.asList(KaraokeActivity.GENRE_LIST).indexOf(focus), 0);
        list.scrollToPosition(at);
        list.post(() -> focusRow(at));
    }

    private void showGenre(KaraokeActivity.Genre g) {
        shownGenre = g;
        shownSinger = null;
        run(g.query, YouTubeSearch.VIDEOS_ONLY, activity.getString(g.label), false);
    }

    private void search(String typed) {
        String q = typed == null ? "" : typed.trim();
        if (q.isEmpty()) {
            if (mode != SINGERS && mode != GENRES) loadTrending();
            return;
        }
        hideKeyboard();
        shownSinger = null;
        shownGenre = null;
        run(SearchParser.query(q), YouTubeSearch.VIDEOS_ONLY, q, false);
    }

    private void run(String q, String filter, String typed, boolean isTrending) {
        int id = ++searchId;
        loading = true;
        useSongRow();
        setMode(isTrending ? TRENDING : RESULTS);
        showKeys(false);
        status.setText(activity.getString(R.string.searching, q));
        worker.execute(() -> {
            SearchParser.Page p;
            try {
                p = YouTubeSearch.search(q, filter);
            } catch (Exception e) {
                android.util.Log.w("Karaoke", "player search failed", e);
                main.post(() -> {
                    if (id != searchId) return;
                    loading = false;
                    status.setText(YouTubeSearch.errorText(e));
                });
                return;
            }
            main.post(() -> {
                if (id != searchId || activity.isDestroyed()) return;
                loading = false;
                if (isTrending) trending = p;
                show(p, typed, true);
            });
        });
    }

    private void show(SearchParser.Page p, String typed, boolean focusFirst) {
        page = p;
        shownQuery = typed;
        songs.clear();
        songs.addAll(p.songs);
        adapter.notifyDataSetChanged();
        list.scrollToPosition(0);
        updateStatus();
        if (songs.isEmpty()) status.setText(R.string.no_results);
        else if (focusFirst || !query.hasFocus()) list.post(() -> focusRow(0));
    }

    private void updateStatus() {
        int n = SongQueue.get(activity).size();
        switch (mode) {
            case FAVORITES:
                status.setText(songs.isEmpty() ? activity.getString(R.string.ps_favorites_empty)
                        : activity.getString(R.string.ps_favorites, songs.size(), n));
                break;
            case SINGERS:
                status.setText(shownSingers.isEmpty()
                        ? activity.getString(R.string.ps_singers_none, query.getText().toString().trim())
                        : activity.getString(R.string.ps_singers, n));
                break;
            case GENRES:
                status.setText(activity.getString(R.string.ps_genres, n));
                break;
            case RESULTS:
                status.setText(shownSinger != null ? activity.getString(R.string.ps_singer, shownSinger, n)
                        : shownGenre != null ? activity.getString(R.string.ps_genre, shownQuery, n)
                        : activity.getString(R.string.ps_results, shownQuery, n));
                break;
            default:
                status.setText(activity.getString(R.string.ps_trending, n));
        }
    }

    private void loadMore() {
        SearchParser.Page from = page;
        if (loading || from == null || from.continuation == null) return;
        loading = true;
        int id = searchId;
        worker.execute(() -> {
            SearchParser.Page more;
            try {
                more = YouTubeSearch.more(from);
            } catch (Exception e) {
                android.util.Log.w("Karaoke", "player more failed", e);
                more = null;
            }
            SearchParser.Page m = more;
            main.post(() -> {
                if (id != searchId) return;
                loading = false;
                page = m != null ? m : new SearchParser.Page(new ArrayList<>(), null, from.clientVersion);
                if (m == null) return;
                int start = songs.size();
                for (Song s : m.songs) {
                    boolean seen = false;
                    for (Song o : songs) if (o.id.equals(s.id)) { seen = true; break; }
                    if (!seen) songs.add(s);
                }
                adapter.notifyItemRangeInserted(start, songs.size() - start);
            });
        });
    }

    private void focusRow(int position) {
        RecyclerView.ViewHolder h = list.findViewHolderForAdapterPosition(position);
        if (h != null) h.itemView.requestFocus();
    }

    private void add(Song s) {
        SongQueue q = SongQueue.get(activity);
        if (q.add(s)) toast(activity.getString(R.string.queued, s.title, q.size()));
        else toast(activity.getString(R.string.already_queued));
        updateStatus();
        adapter.notifyItemRangeChanged(0, songs.size());
    }

    private void singNext(Song s) {
        SongQueue.get(activity).addFirst(s);
        toast(activity.getString(R.string.prioritized, s.title));
        updateStatus();
        adapter.notifyItemRangeChanged(0, songs.size());
    }

    private void toggleFavorite(Song s) {
        boolean now = Library.get(activity).toggleFavorite(s);
        toast(activity.getString(now ? R.string.favorited : R.string.unfavorited));
        if (mode == FAVORITES) showFavorites();
        else adapter.notifyItemRangeChanged(0, songs.size());
    }

    private void toast(String text) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        final class Holder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView title, meta, duration;

            Holder(View v) {
                super(v);
                thumb = v.findViewById(R.id.thumb);
                title = v.findViewById(R.id.title);
                meta = v.findViewById(R.id.meta);
                duration = v.findViewById(R.id.duration);
                v.setOnClickListener(x -> {
                    Song s = at();
                    if (s != null) add(s);
                });
                v.setOnLongClickListener(x -> {
                    Song s = at();
                    if (s != null) singNext(s);
                    return true;
                });
                v.setOnKeyListener((x, code, e) -> {
                    if (code != KeyEvent.KEYCODE_MENU) return false;
                    Song s = at();
                    if (s != null && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) toggleFavorite(s);
                    return true;
                });
            }

            Song at() {
                int p = getBindingAdapterPosition();
                return p >= 0 && p < songs.size() ? songs.get(p) : null;
            }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_row, parent, false));
        }

        @Override public void onBindViewHolder(Holder h, int position) {
            Song s = songs.get(position);
            h.title.setText(s.title);
            String meta = s.views.isEmpty() ? s.channel : s.channel + " · " + s.views;
            if (Library.get(activity).isFavorite(s.id)) meta = activity.getString(R.string.favorite_mark) + " " + meta;
            h.meta.setText(SongQueue.get(activity).contains(s.id)
                    ? activity.getString(R.string.in_queue) + " · " + meta : meta);
            h.duration.setText(s.duration);
            Thumbnails.load(h.thumb, s.thumbnail());
            if (position >= songs.size() - 4) list.post(PlayerSearch.this::loadMore);
        }

        @Override public int getItemCount() {
            return songs.size();
        }
    }

    private final class SingerAdapter extends RecyclerView.Adapter<SingerAdapter.Holder> {
        final class Holder extends RecyclerView.ViewHolder {
            final ImageView photo;
            final TextView initials, name;

            Holder(View v) {
                super(v);
                photo = v.findViewById(R.id.photo);
                photo.setClipToOutline(true);
                initials = v.findViewById(R.id.initials);
                name = v.findViewById(R.id.name);
                v.setOnClickListener(x -> {
                    int p = getBindingAdapterPosition();
                    if (p >= 0 && p < shownSingers.size()) showSinger(shownSingers.get(p));
                });
            }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_singer_row, parent, false));
        }

        @Override public void onBindViewHolder(Holder h, int position) {
            String s = shownSingers.get(position);
            h.name.setText(s);
            h.initials.setText(KaraokeActivity.initials(s));
            Avatars.load(activity, h.photo, s);
        }

        @Override public int getItemCount() {
            return shownSingers.size();
        }
    }

    private final class GenreAdapter extends RecyclerView.Adapter<GenreAdapter.Holder> {
        final class Holder extends RecyclerView.ViewHolder {
            final TextView name;

            Holder(View v) {
                super(v);
                name = v.findViewById(R.id.genre_name);
                v.setOnClickListener(x -> {
                    int p = getBindingAdapterPosition();
                    if (p >= 0) showGenre(KaraokeActivity.GENRE_LIST[p]);
                });
            }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_genre_row, parent, false));
        }

        @Override public void onBindViewHolder(Holder h, int position) {
            KaraokeActivity.Genre g = KaraokeActivity.GENRE_LIST[position];
            h.name.setText(g.label);
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{g.from, g.to});
            bg.setCornerRadius(10 * activity.getResources().getDisplayMetrics().density);
            h.itemView.setBackground(bg);
        }

        @Override public int getItemCount() {
            return KaraokeActivity.GENRE_LIST.length;
        }
    }
}
