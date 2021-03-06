data = load ('../benchmarkdata/DvcInsertionsReal2016-05-31-14:21:59.data');

pageWidth  = 426.79135;
pageHeight = pageWidth / sqrt(2);

Hfig = figure(1);



x  = data(:,1); % MODIFICATIONS
y1 = data(:,4); % ELAPSED TIME
y2 = data(:,3) .* 1024; % HEAP SIZE

yyaxis left;
HP(1) = plot(x, y1, 'd-', 'color', [0.5, 0, 0],'markersize', 6, 'markerfacecolor', [0.5, 0, 0], 'displayname', 'Time' );
ylabel ('Elapsed time (s)', 'fontsize', 16);
ylim([0, 50]);

grid on;
hold on;

yyaxis right;
HP(2) = plot(x, y2, 'p-', 'color', [0, 0.5, 0],'markersize', 6, 'markerfacecolor', [0, 0.5, 0], 'displayname', 'Memory' );
ylabel ('Memory usage (MB)', 'fontsize', 16);
xlim([0, 1100]);
ylim([0, 350]);

hold off;

xlabel ('Inserted edges (Millions)', 'fontsize', 16);

set(HP,'Linewidth', 2);    


HL = legend (HP);
set(HL, 'fontsize', 16, 'location', 'northwest');

set(gca, 'fontsize', 14 );
set(gca, 'ticklength', [0.02, 0.05]);


% PRINT PDF

filename = 'benchmarkDvcInsertion.pdf';

set(Hfig , 'units', 'points', 'paperunits', 'points', 'paperposition',  [0, 0, pageWidth, pageHeight], 'papersize', [pageWidth, pageHeight], 'position', [0, 0, pageWidth, pageHeight], 'name', filename, 'filename', filename);

imStyle = hgexport('factorystyle');

imStyle.Format = 'pdf';
imStyle.Width = pageWidth;
imStyle.Height = pageHeight;
imStyle.Units = 'points';


hgexport(gcf, filename, imStyle, 'Format', 'pdf');

