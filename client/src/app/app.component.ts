import {ChangeDetectionStrategy, Component, OnDestroy, OnInit} from '@angular/core';
import {AppService} from './app.service';
import {Observable, Subscription} from 'rxjs';
import {Track} from './model/track.model';
import {filter} from 'rxjs/operators';

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppComponent implements OnInit, OnDestroy {
    searchResults$: Observable<Track[]>;
    playlist$: Observable<Track[]>;
    current$: Observable<Track>;
    volume$: Observable<number>;

    private sub = new Subscription();

    constructor(private service: AppService) {
        this.searchResults$ = this.service.getSearchResults();
        this.playlist$ = this.service.getPlaylist();
        this.current$ = this.service.getCurrentTrack();
        this.volume$ = this.service.getVolume();
    }

    search(query: string) {
        this.service.search(query);
    }

    addTrack(track: Track) {
        this.service.addTrack(track);
    }

    ngOnInit(): void {
        this.sub.add(
            this.service.getMessages()
                .pipe(filter(msg => msg && msg.length > 0))
                .subscribe(alert)
        );
    }

    skipTrack() {
        this.service.skipTrack();
    }

    togglePlay() {
        this.service.togglePlay();
    }

    ngOnDestroy(): void {
        this.sub.unsubscribe();
    }

    changeVolume(volume: number) {
        this.service.changeVolume(volume);
    }
}
