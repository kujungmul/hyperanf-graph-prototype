data = load ("benchmarksdvc.data");
#data2 = load ("benchmarks.data");

nrBreaks = 10;
x  = data(:,1)';
y1 = data(:,2)';
y2 = data(:,3)';
y2polf = polyfit(x, y2, nrBreaks);
y3 = data(:,4)';

#nrBreaks2 = 15;
#x2 = data2(:,1)';
#y12 = data2(:,2)';
#y22 = data2(:,3)';
#y22polf = polyfit(x2, y22, nrBreaks2);
[ax, h1, h2] = plotyy (x, y3, x, polyval(y2polf, x));
#ax = plotyy (data (:,1), data (:,2), data (:,1), data (:,3));

set(h2, "Linestyle", "--");
set(h1,'Linewidth', 1.5); 
set(h2,'Linewidth', 1.5); 
set(ax(1), 'ylim', [0 100])
set(ax(2), 'ylim', [0 3])
xlabel ("Inserted edges (Millions)");
ylabel (ax(1), "Elapsed time (Seconds)");
ylabel (ax(2), "Heap-size (GigaByte)");

legend ([h1, h2], {"Time", "Memory"}) 
#print('firstIteration.png','-dpng','-r600');
