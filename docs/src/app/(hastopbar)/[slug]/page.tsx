import { InlineTOC } from 'fumadocs-ui/components/inline-toc';
import defaultMdxComponents from 'fumadocs-ui/mdx';
import { notFound } from 'next/navigation';
import React from 'react';
import type { Metadata } from 'next';
import { legal } from '@/lib/source';
import { createMetadata } from '@/utils/metadata';

interface Param {
  slug: string;
}

export default async function Page({ params }: { params: Promise<Param> }) {
  const page = legal.getPage([(await params).slug]);

  if (!page) notFound();

  return (
    <div>
      <div
        className="container rounded-xl border py-12 md:px-8"
        style={{
          backgroundColor: 'black',
          backgroundImage: [
            'linear-gradient(140deg, hsla(224,34%,84%,0.3), transparent 50%)',
            'linear-gradient(to left top, hsla(200,90%,50%,0.8), transparent 50%)',
            'radial-gradient(circle at 100% 100%, hsla(100,100%,40%,1), hsla(240,40%,40%,1) 17%, hsla(240,40%,40%,0.5) 20%, transparent)',
          ].join(', '),
          backgroundBlendMode: 'difference, difference, normal',
        }}
      >
        <h1 className="mb-2 text-3xl font-bold text-white">{page.data.title}</h1>
        <p className="mb-4 text-white/80">{page.data.description}</p>
      </div>
      <article className="container grid grid-cols-1 px-0 py-8 lg:grid-cols-[2fr_1fr] lg:px-4">
        <div className="prose p-4">
          <InlineTOC items={page.data.toc} />
          <page.data.body components={defaultMdxComponents} />
        </div>
      </article>
    </div>
  );
}

export async function generateMetadata({ params }: { params: Promise<Param> }): Promise<Metadata> {
  const page = legal.getPage([(await params).slug]);

  if (!page) notFound();

  return createMetadata({
    title: page.data.title,
    description: page.data.description ?? 'The library for building documentation sites',
  });
}

export function generateStaticParams(): Param[] {
  return legal.getPages().map((page) => ({
    slug: page.slugs[0],
  }));
}
