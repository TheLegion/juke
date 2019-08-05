import {Injectable} from '@angular/core';
import {merge, Observable, Subject} from 'rxjs';
import {RxStompService} from '@stomp/ng2-stompjs';
import {Track} from './model/track.model';
import {map, shareReplay, switchMap} from 'rxjs/operators';
import {PlayerState} from './model/player-state.model';

@Injectable({
    providedIn: 'root'
})
export class AppService {

    private readonly searchQuery$: Subject<string> = new Subject();
    private readonly skip$: Subject<void> = new Subject<void>();
    private readonly searchResults$: Observable<Track[]>;
    private readonly playlist$: Observable<Track[]>;
    private readonly currentTrack$: Observable<Track>;
    private readonly volume$: Observable<number>;
    private readonly messages$: Observable<string>;
    private readonly playDuration$: Observable<number>;

    constructor(private backend: RxStompService) {
        const fullInfo: Observable<PlayerState> = this.backend.watch('/app/player/state').pipe(map(parse), shareReplay(1));
        this.searchResults$ = this.searchQuery$.pipe(
            switchMap(query => this.backend.watch('/app/search', {query})),
            map(parse)
        );
        this.playlist$ = merge(
            this.backend.watch('/player/playlist').pipe(map(parse)),
            fullInfo.pipe(map(info => info.playlist))
        ).pipe(shareReplay(1));
        this.currentTrack$ = merge(
            this.backend.watch('/player/current').pipe(map(parse)),
            fullInfo.pipe(map(info => info.currentTrack))
        ).pipe(shareReplay(1));
        this.volume$ = merge(
            this.backend.watch('/player/volume').pipe(map(parse)),
            fullInfo.pipe(map(info => info.volume))
        );
        this.messages$ = this.skip$.pipe(
            switchMap(() => backend.watch('/app/player/skip')),
            map(msg => msg.body)
        );
        this.playDuration$ = fullInfo.pipe(map(info => info.playDuration));
    }

    getSearchResults(): Observable<Track[]> {
        return this.searchResults$;
    }

    getPlaylist(): Observable<Track[]> {
        return this.playlist$;
    }

    getCurrentTrack() {
        return this.currentTrack$;
    }

    getVolume() {
        return this.volume$;
    }

    getMessages() {
        return this.messages$;
    }

    getPlayDuration() {
        return this.playDuration$;
    }

    search(query: string) {
        this.searchQuery$.next(query);
    }

    addTrack(track: Track): void {
        this.backend.publish({destination: '/app/player/add', body: JSON.stringify(track)});
    }

    skipTrack() {
        this.skip$.next();
    }

    togglePlay() {
        this.backend.publish({destination: '/app/player/toggle-play'});
    }

    changeVolume(volume: number) {
        this.backend.publish({destination: '/app/player/volume', body: `${volume}`});
    }

    setTrackPosition(data: Track, position: number) {
        this.backend
            .publish({
                destination: '/app/player/position', body: JSON.stringify({trackId: data.id, position})
            });
    }
}

function parse<T = any>(msg): T {
    return JSON.parse(msg.body);
}
