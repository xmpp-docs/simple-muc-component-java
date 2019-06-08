all: index.html

index.html:
	pandoc -s --toc README.md --css pandoc.css -o index.html

clean:
	rm index.html
